package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
      assertThat(process.trace())
          .containsSubsequence(
              "IN go depth 8 movetime 1",
              "IN stop",
              "OUT info string bestmove a2a3",
              "OUT bestmove a2a3",
              "IN isready",
              "OUT readyok",
              "IN position startpos",
              "IN go movetime 10",
              "OUT bestmove e2e4");
      engine.close();
    }
  }

  @Test
  void completedEvaluationWithMissingScoreDoesNotWaitForAnotherBestMove() {
    try (ScriptedUciProcess process = ScriptedUciProcess.bestmoveWithoutScore()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

      engine.setPosition("startpos");
      assertThatThrownBy(() -> engine.evaluate(8, Duration.ofMillis(10)))
          .isInstanceOf(StockfishException.class)
          .hasMessageContaining("without an evaluation score");

      engine.setPosition("startpos");
      assertThat(engine.bestMove(Duration.ofMillis(10))).isEqualTo("e2e4");
      assertThat(process.commands()).doesNotContain("stop");
      engine.close();
    }
  }

  @Test
  void completedBestMoveValidationFailureDoesNotDrainAnotherSearch() {
    try (ScriptedUciProcess process = ScriptedUciProcess.malformedBestMove()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());

      engine.setPosition("startpos");
      assertThatThrownBy(() -> engine.bestMove(Duration.ofMillis(10)))
          .isInstanceOf(StockfishException.class)
          .hasMessageContaining("Unexpected bestmove response");

      engine.setPosition("startpos");
      assertThat(engine.bestMove(Duration.ofMillis(10))).isEqualTo("e2e4");
      assertThat(process.commands()).doesNotContain("stop");
      engine.close();
    }
  }

  @Test
  void interruptedReadFailsClosedWithoutDiscardingPendingRead() throws Exception {
    try (ScriptedUciProcess process = ScriptedUciProcess.recoverable()) {
      StockfishEngine engine = new StockfishEngine(process, stockfishConfig());
      engine.setPosition("startpos");
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread caller =
          new Thread(
              () -> {
                try {
                  engine.bestMove(Duration.ofMillis(10));
                } catch (Throwable throwable) {
                  failure.set(throwable);
                }
              },
              "interrupted-stockfish-caller");

      caller.start();
      awaitCommand(process, "go movetime 10");
      caller.interrupt();
      caller.join(2_000);

      assertThat(caller.isAlive()).isFalse();
      assertThat(failure.get()).isInstanceOf(StockfishException.class);
      assertThatThrownBy(() -> engine.bestMove(Duration.ofMillis(10)))
          .isInstanceOf(StockfishException.class)
          .hasMessageContaining("not usable");
      engine.close();
    }
  }

  private static void awaitCommand(ScriptedUciProcess process, String expected)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (!process.commands().contains(expected) && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertThat(process.commands()).contains(expected);
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

  private static final class InterruptIgnoringInputStream extends InputStream {

    private final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    void emitLine(String line) {
      byte[] encoded = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
      for (byte value : encoded) {
        bytes.add(value & 0xFF);
      }
    }

    @Override
    public int read() {
      while (true) {
        Integer value = bytes.poll();
        if (value != null) {
          return value;
        }
        if (closed) {
          return -1;
        }
        try {
          Thread.sleep(5);
        } catch (InterruptedException ignored) {
          // Deliberately ignore interruption to model a process-pipe read that
          // Future.cancel(true) cannot safely detach from the underlying stream.
        }
      }
    }

    @Override
    public int read(byte[] target, int offset, int length) {
      int first = read();
      if (first < 0) {
        return -1;
      }
      target[offset] = (byte) first;
      int count = 1;
      while (count < length) {
        Integer value = bytes.poll();
        if (value == null) {
          break;
        }
        target[offset + count] = (byte) (int) value;
        count++;
      }
      return count;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class ScriptedUciProcess extends Process implements AutoCloseable {

    private enum Mode {
      RECOVERABLE_TIMEOUT,
      UNRECOVERABLE_TIMEOUT,
      BESTMOVE_WITHOUT_SCORE,
      MALFORMED_BESTMOVE
    }

    private final InterruptIgnoringInputStream clientReads = new InterruptIgnoringInputStream();
    private final PipedInputStream engineReads = new PipedInputStream();
    private final PipedOutputStream clientWrites;
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private final List<String> trace = new CopyOnWriteArrayList<>();
    private final Mode mode;
    private final Thread engineThread;
    private volatile boolean alive = true;

    private ScriptedUciProcess(Mode mode) {
      try {
        clientWrites = new PipedOutputStream(engineReads);
      } catch (IOException exception) {
        throw new IllegalStateException(exception);
      }
      this.mode = mode;
      engineThread = new Thread(this::runEngine, "scripted-uci-engine");
      engineThread.setDaemon(true);
      engineThread.start();
    }

    static ScriptedUciProcess recoverable() {
      return new ScriptedUciProcess(Mode.RECOVERABLE_TIMEOUT);
    }

    static ScriptedUciProcess unrecoverable() {
      return new ScriptedUciProcess(Mode.UNRECOVERABLE_TIMEOUT);
    }

    static ScriptedUciProcess bestmoveWithoutScore() {
      return new ScriptedUciProcess(Mode.BESTMOVE_WITHOUT_SCORE);
    }

    static ScriptedUciProcess malformedBestMove() {
      return new ScriptedUciProcess(Mode.MALFORMED_BESTMOVE);
    }

    List<String> commands() {
      return List.copyOf(commands);
    }

    List<String> trace() {
      return List.copyOf(trace);
    }

    private void runEngine() {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(engineReads))) {
        String command;
        while (alive && (command = reader.readLine()) != null) {
          commands.add(command);
          trace.add("IN " + command);
          switch (command) {
            case "uci" -> {
              emitLine("id name ScriptedStockfish");
              emitLine("uciok");
            }
            case "isready" -> {
              if (mode != Mode.UNRECOVERABLE_TIMEOUT
                  || commands.stream().noneMatch("go depth 8 movetime 1"::equals)) {
                emitLine("readyok");
              } else {
                clientReads.close();
                alive = false;
              }
            }
            case "stop" -> {
              if (mode == Mode.RECOVERABLE_TIMEOUT) {
                emitLine("info string bestmove a2a3");
                Thread delayedBestmove =
                    new Thread(
                        () -> {
                          try {
                            Thread.sleep(75);
                            emitLine("bestmove a2a3");
                          } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                          }
                        },
                        "scripted-delayed-stale-bestmove");
                delayedBestmove.setDaemon(true);
                delayedBestmove.start();
              }
            }
            case "go depth 8 movetime 1" -> {
              if (mode == Mode.RECOVERABLE_TIMEOUT) {
                emitLine("bestmoveX");
              }
            }
            case "go depth 8 movetime 10" -> {
              if (mode == Mode.BESTMOVE_WITHOUT_SCORE) {
                emitLine("bestmove a2a3");
              }
            }
            case "go movetime 10" -> {
              if (mode == Mode.MALFORMED_BESTMOVE
                  && commands.stream().filter("go movetime 10"::equals).count() == 1) {
                emitLine("bestmove");
              } else {
                Thread validBestmove =
                    new Thread(
                        () -> {
                          try {
                            Thread.sleep(150);
                            emitLine("bestmove e2e4");
                          } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                          }
                        },
                        "scripted-delayed-valid-bestmove");
                validBestmove.setDaemon(true);
                validBestmove.start();
              }
            }
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

    private synchronized void emitLine(String line) {
      trace.add("OUT " + line);
      clientReads.emitLine(line);
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
      } catch (IOException ignored) {
        // Test resource cleanup only.
      }
    }
  }
}
