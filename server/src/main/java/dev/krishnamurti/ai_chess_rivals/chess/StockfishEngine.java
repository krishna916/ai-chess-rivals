package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Package-private UCI client that communicates with the Stockfish chess engine
 * over stdin/stdout. Implements the public {@link StockfishClient} interface.
 *
 * <p>The executable path comes exclusively from {@link ChessProperties}.
 * No OS detection is performed here — the application executes whatever path is configured.
 *
 * <p>All UCI communication is single-threaded (serialized). Do not call
 * methods on this class from multiple threads concurrently.
 */
@Slf4j
class StockfishEngine implements StockfishClient {

    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    StockfishEngine(ChessProperties chessProperties) {
        ChessProperties.Stockfish config = chessProperties.stockfish();
        Path executablePath = Paths.get(config.path());

        log.info("Stockfish executable configured at: {}", executablePath.toAbsolutePath());

        if (!Files.exists(executablePath)) {
            throw new StockfishException(
                    """
                    Stockfish executable not found at: %s

                    To fix this, download the correct binary for your platform:

                      Windows:  mvn package -Pwindows    (from the server/ directory)
                      Linux:    mvn package -Plinux      (from the server/ directory)

                    Then set STOCKFISH_PATH if using a non-default location:

                      Windows:  STOCKFISH_PATH=stockfish/stockfish.exe
                      Linux:    STOCKFISH_PATH=stockfish/stockfish

                    See server/README.md for full instructions.
                    """.formatted(executablePath.toAbsolutePath())
            );
        }

        if (!Files.isExecutable(executablePath)) {
            throw new StockfishException(
                    "Stockfish file exists at '%s' but is not executable. On Linux, run: chmod +x %s"
                            .formatted(executablePath.toAbsolutePath(), executablePath.toAbsolutePath())
            );
        }

        try {
            log.info("Launching Stockfish: {}", executablePath.toAbsolutePath());
            this.process = new ProcessBuilder(executablePath.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();

            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            performUciHandshake();
            configureOptions(config);
            waitForReady();

        } catch (IOException e) {
            throw new StockfishException("Failed to launch Stockfish process", e);
        }

        log.info("Stockfish ready. Threads={}, Hash={}MB", config.threads(), config.hashMb());
    }

    @Override
    public void newGame() {
        try {
            sendCommand(UciCommand.newGame());
            waitForReady();
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
            UciResponse response = waitForToken("bestmove");
            return response.extractBestMove()
                    .orElseThrow(() -> new StockfishException(
                            "Unexpected bestmove response: " + response.raw()));
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
        String rawLine;

        while ((rawLine = reader.readLine()) != null) {
            UciResponse response = new UciResponse(rawLine);
            log.debug("<<< {}", rawLine);
            engineName = response.extractField("id name ").orElse(engineName);
            engineAuthor = response.extractField("id author ").orElse(engineAuthor);
            if (response.contains("uciok")) {
                log.info("UCI handshake complete. Engine: {} by {}", engineName, engineAuthor);
                return;
            }
        }
        throw new StockfishException("Stockfish process ended before responding with 'uciok'");
    }

    private void configureOptions(ChessProperties.Stockfish config) throws IOException {
        sendCommand(UciCommand.setOption("Threads", config.threads()));
        sendCommand(UciCommand.setOption("Hash", config.hashMb()));
    }

    private void waitForReady() throws IOException {
        sendCommand(UciCommand.isReady());
        waitForToken("readyok");
    }

    private void sendCommand(UciCommand command) throws IOException {
        writer.write(command.text());
        writer.newLine();
        writer.flush();
        log.debug(">>> {}", command);
    }

    private UciResponse waitForToken(String expected) throws IOException {
        String rawLine;
        while ((rawLine = reader.readLine()) != null) {
            UciResponse response = new UciResponse(rawLine);
            log.debug("<<< {}", rawLine);
            if (response.contains(expected)) {
                return response;
            }
        }
        throw new StockfishException(
                "Stockfish process ended before responding with expected token: " + expected);
    }
}
