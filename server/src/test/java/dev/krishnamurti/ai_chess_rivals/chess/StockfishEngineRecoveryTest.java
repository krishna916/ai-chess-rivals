package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StockfishEngineRecoveryTest {

  @Test
  void timedOutEvaluationDoesNotLeakItsBestMoveIntoNextMoveSearch() {
    try (ScriptedUciProcess process = ScriptedUciProcess.recoverable()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

      engine.setPosition("startpos");
      assertThatThrownBy(() -> engine.evaluate(8, Duration.ofMillis(1)))
          .isInstanceOf(StockfishException.class);

      engine.setPosition("startpos");
      String move = engine.bestMove(Duration.ofMillis(10));

      assertThat(move).isEqualTo("e2e4");
      assertThat(process.commands())
          .containsSubsequence(
              "go depth 8 movetime 1", "stop", "isready", "position startpos", "go movetime 10");
      engine.close();
    }
  }

  @Test
  void failedRecoveryMarksEngineUnusableInsteadOfAllowingAnotherSearch() {
    try (ScriptedUciProcess process = ScriptedUciProcess.unrecoverable()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

      engine.setPosition("startpos");
      assertThatThrownBy(() -> engine.evaluate(8, Duration.ofMillis(1)))
          .isInstanceOf(StockfishException.class);

      int commandsAfterFailure = process.commands().size();
      assertThatThrownBy(() -> engine.setPosition("startpos"))
          .isInstanceOf(StockfishException.class)
          .hasMessageContaining("not usable after failed search recovery");
      assertThat(process.commands()).hasSize(commandsAfterFailure);
      engine.close();
    }
  }

  private static ChessProperties.Stockfish stockfishConfig() {
    return new ChessProperties.Stockfish(
        "unused-for-injected-process",
        1,
        16,
        1,
        1,
        new ChessProperties.Stockfish.Evaluation(8, 50, 200, 200));
  }

  private static final class ScriptedUciProcess extends Process implements AutoCloseable {

    private final PipedInputStream clientReads = new PipedInputStream();
    private final PipedOutputStream engineWrites;
    private final PipedInputStream engineReads = new PipedInputStream();
    private final PipedOutputStream clientWrites;
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private final boolean recoverable;
    private final Thread engineThread;
    private volatile boolean alive = true;

    private ScriptedUciProcess(boolean recoverable) {
      try {
        engineWrites = new PipedOutputStream(clientReads);
        clientWrites = new PipedOutputStream(engineReads);
      } catch (IOException exception) {
        throw new IllegalStateException(exception);
      }
      this.recoverable = recoverable;
      engineThread = new Thread(this::runEngine, "scripted-uci-engine");
      engineThread.setDaemon(true);
      engineThread.start();
    }

    static ScriptedUciProcess recoverable() {
      return new ScriptedUciProcess(true);
    }

    static ScriptedUciProcess unrecoverable() {
      return new ScriptedUciProcess(false);
    }

    List<String> commands() {
      return List.copyOf(commands);
    }

    private void runEngine() {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(engineReads));
          BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(engineWrites))) {
        String command;
        while (alive && (command = reader.readLine()) != null) {
          commands.add(command);
          switch (command) {
            case "uci" -> writeLine(writer, "id name ScriptedStockfish", "uciok");
            case "isready" -> {
              if (recoverable || commands.stream().noneMatch("go depth 8 movetime 1"::equals)) {
                writeLine(writer, "readyok");
              } else {
                engineWrites.close();
                alive = false;
              }
            }
            case "stop" -> {
              if (recoverable) {
                writeLine(writer, "bestmove a2a3");
              }
            }
            case "go movetime 10" -> writeLine(writer, "bestmove e2e4");
            case "quit" -> alive = false;
            default -> {
              // position/setoption/ucinewgame are accepted without direct output.
            }
          }
        }
      } catch (IOException ignored) {
        alive = false;
      }
    }

    private static void writeLine(BufferedWriter writer, String... lines) throws IOException {
      for (String line : lines) {
        writer.write(line);
        writer.newLine();
      }
      writer.flush();
    }

    @Override
    public OutputStream getOutputStream() {
      return clientWrites;
    }

    @Override
    public InputStream getInputStream() {
      return clientReads;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
      engineThread.join();
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      engineThread.join(unit.toMillis(timeout));
      return !engineThread.isAlive();
    }

    @Override
    public int exitValue() {
      if (alive) {
        throw new IllegalThreadStateException("process still alive");
      }
      return 0;
    }

    @Override
    public void destroy() {
      close();
    }

    @Override
    public boolean isAlive() {
      return alive;
    }

    @Override
    public void close() {
      alive = false;
      try {
        clientWrites.close();
        clientReads.close();
        engineReads.close();
        engineWrites.close();
      } catch (IOException ignored) {
        // Test resource cleanup only.
      }
    }
  }
}
