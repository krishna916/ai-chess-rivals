package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Package-private UCI client that communicates with the Stockfish chess engine over stdin/stdout.
 * Implements the public {@link StockfishClient} interface.
 *
 * <p>The executable path comes exclusively from {@link ChessProperties}. No OS detection is
 * performed here — the application executes whatever path is configured.
 *
 * <p>All UCI communication is single-threaded (serialized). Do not call methods on this class from
 * multiple threads concurrently.
 *
 * <p>Startup waits (UCI handshake, initial readyok) are bounded by {@code startupTimeoutSeconds}.
 * Gameplay waits (newGame, bestMove) are bounded by {@code moveTimeoutSeconds}. A silent Stockfish
 * process cannot hang either phase indefinitely.
 */
@Slf4j
final class StockfishEngine implements StockfishClient {

  private final Process process;
  private final BufferedReader reader;
  private final BufferedWriter writer;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final long startupTimeoutSeconds;
  private final long moveTimeoutSeconds;

  /**
   * Single-threaded executor used solely to submit blocking {@code readLine()} calls so they can be
   * cancelled via {@link Future#get(long, TimeUnit)}.
   */
  private final ExecutorService lineReaderExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "stockfish-reader");
            t.setDaemon(true);
            return t;
          });

  StockfishEngine(ChessProperties chessProperties) {
    ChessProperties.Stockfish config = chessProperties.stockfish();
    Path executablePath = Paths.get(config.path());

    this.startupTimeoutSeconds = config.startupTimeoutSeconds();
    this.moveTimeoutSeconds = config.moveTimeoutSeconds();

    log.info("Stockfish executable configured at: {}", executablePath.toAbsolutePath());

    if (!Files.exists(executablePath)) {
      String message =
          String.join(
              System.lineSeparator(),
              "Stockfish executable not found at: %s",
              "",
              "To fix this, download the correct binary for your platform:",
              "",
              "  Windows:  mvn package -Pwindows    (from the server/ directory)",
              "  Linux:    mvn package -Plinux      (from the server/ directory)",
              "",
              "Then set STOCKFISH_PATH if using a non-default location:",
              "",
              "  Windows:  STOCKFISH_PATH=stockfish/stockfish.exe",
              "  Linux:    STOCKFISH_PATH=stockfish/stockfish",
              "",
              "See server/README.md for full instructions.");
      throw new StockfishException(message.formatted(executablePath.toAbsolutePath()));
    }

    if (!Files.isExecutable(executablePath)) {
      throw new StockfishException(
          "Stockfish file exists at '%s' but is not executable. On Linux, run: chmod +x %s"
              .formatted(executablePath.toAbsolutePath(), executablePath.toAbsolutePath()));
    }

    try {
      log.info("Launching Stockfish: {}", executablePath.toAbsolutePath());
      this.process =
          new ProcessBuilder(executablePath.toAbsolutePath().toString())
              .redirectErrorStream(true)
              .start();

      this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

      performUciHandshake();
      configureOptions(config);
      waitForReady(startupTimeoutSeconds);

    } catch (IOException e) {
      throw new StockfishException("Failed to launch Stockfish process", e);
    }

    log.info("Stockfish ready. Threads={}, Hash={}MB", config.threads(), config.hashMb());
  }

  @Override
  public void newGame() {
    try {
      sendCommand(UciCommand.newGame());
      waitForReady(moveTimeoutSeconds);
    } catch (IOException e) {
      throw new StockfishException("Failed to send ucinewgame", e);
    }
  }

  @Override
  public void setPosition(String fen) {
    try {
      sendCommand(UciCommand.position(fen));
    } catch (IOException e) {
      throw new StockfishException("Failed to send position command", e);
    }
  }

  @Override
  public String bestMove(Duration thinkTime) {
    try {
      sendCommand(UciCommand.go(thinkTime.toMillis()));
      // Add a safety margin on top of the engine think time so a normally
      // running engine is never interrupted, but a silent/hung engine is.
      long safetyMarginSeconds = moveTimeoutSeconds;
      long deadlineSeconds = thinkTime.toSeconds() + safetyMarginSeconds;
      UciResponse response = waitForToken("bestmove", deadlineSeconds);
      return response
          .extractBestMove()
          .orElseThrow(
              () -> new StockfishException("Unexpected bestmove response: " + response.raw()));
    } catch (IOException e) {
      throw new StockfishException("Failed to obtain best move from Stockfish", e);
    }
  }

  @Override
  @PreDestroy
  public void close() {
    if (closed.getAndSet(true)) {
      return; // idempotent — second call is a no-op
    }
    lineReaderExecutor.shutdownNow();
    try {
      sendCommand(UciCommand.quit());
    } catch (IOException e) {
      log.warn("Could not send quit command to Stockfish: {}", e.getMessage());
    }
    process.destroy();
    log.info("Stockfish process terminated.");
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  private void performUciHandshake() throws IOException {
    sendCommand(UciCommand.uci());
    String engineName = "Unknown";
    String engineAuthor = "Unknown";

    while (true) {
      String rawLine = readLineWithTimeout(startupTimeoutSeconds, "UCI handshake (uciok)");
      if (rawLine == null) {
        throw new StockfishException("Stockfish process ended before responding with 'uciok'");
      }
      UciResponse response = new UciResponse(rawLine);
      log.debug("<<< {}", rawLine);
      engineName = response.extractField("id name ").orElse(engineName);
      engineAuthor = response.extractField("id author ").orElse(engineAuthor);
      if (response.contains("uciok")) {
        log.info("UCI handshake complete. Engine: {} by {}", engineName, engineAuthor);
        return;
      }
    }
  }

  private void configureOptions(ChessProperties.Stockfish config) throws IOException {
    sendCommand(UciCommand.setOption("Threads", config.threads()));
    sendCommand(UciCommand.setOption("Hash", config.hashMb()));
  }

  /** Sends {@code isready} and waits for {@code readyok} within {@code timeoutSeconds}. */
  private void waitForReady(long timeoutSeconds) throws IOException {
    sendCommand(UciCommand.isReady());
    waitForToken("readyok", timeoutSeconds);
  }

  private void sendCommand(UciCommand command) throws IOException {
    writer.write(command.text());
    writer.newLine();
    writer.flush();
    log.debug(">>> {}", command);
  }

  /**
   * Reads lines from Stockfish until one containing {@code expected} is found, or until {@code
   * timeoutSeconds} elapses with no response.
   *
   * @throws StockfishException if the deadline expires or the process exits unexpectedly.
   */
  private UciResponse waitForToken(String expected, long timeoutSeconds) throws IOException {
    while (true) {
      String rawLine = readLineWithTimeout(timeoutSeconds, "token '" + expected + "'");
      if (rawLine == null) {
        throw new StockfishException(
            "Stockfish process ended before responding with expected token: " + expected);
      }
      UciResponse response = new UciResponse(rawLine);
      log.debug("<<< {}", rawLine);
      if (response.contains(expected)) {
        return response;
      }
    }
  }

  /**
   * Submits a single {@link BufferedReader#readLine()} call to the dedicated reader thread and
   * blocks for at most {@code timeoutSeconds}.
   *
   * @param timeoutSeconds maximum wait in seconds
   * @param waitingFor human-readable description for the timeout error message
   * @return the line read, or {@code null} if the stream ended (EOF)
   * @throws StockfishException if the timeout elapses before a line arrives
   * @throws IOException if the underlying read fails
   */
  private String readLineWithTimeout(long timeoutSeconds, String waitingFor) throws IOException {
    Future<String> future = lineReaderExecutor.submit(reader::readLine);
    try {
      return future.get(timeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new StockfishException(
          ("Stockfish did not respond within %d s while waiting for %s. "
                  + "The engine process may be hung or unresponsive.")
              .formatted(timeoutSeconds, waitingFor));
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException ioe) {
        throw ioe;
      }
      throw new StockfishException("Unexpected error reading from Stockfish", cause);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new StockfishException("Interrupted while waiting for Stockfish response");
    }
  }
}
