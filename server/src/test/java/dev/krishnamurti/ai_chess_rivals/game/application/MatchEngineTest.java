package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchEngineTest {

  @Test
  void startNewMatchInitializesFreshState() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient();
    MatchEngine matchEngine =
        new MatchEngine(stockfishClient, new ChessBoardService(), new GameProperties(250, 300));

    Match match = matchEngine.startNewMatch();

    assertEquals(1, stockfishClient.newGameCalls);
    assertTrue(match.isInProgress());
    assertEquals(0, match.moveCount());
    assertEquals(match, matchEngine.currentMatch());
  }

  @Test
  void playUntilFinishedRecordsMovesAndStopsAtMaxPliesFallback() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4", "e7e5");
    MatchEngine matchEngine =
        new MatchEngine(stockfishClient, new ChessBoardService(), new GameProperties(250, 2));

    Match finalMatch = matchEngine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
    assertEquals(GameStatus.FINISHED, finalMatch.status());
    assertEquals(GameResult.DRAW, finalMatch.result().orElseThrow());
    assertEquals(2, finalMatch.moveCount());
    assertEquals("e2e4", finalMatch.moves().get(0).notation().value());
    assertEquals("e7e5", finalMatch.moves().get(1).notation().value());
    assertEquals(2, stockfishClient.bestMoveCalls.size());
    assertEquals(
        List.of(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
        stockfishClient.positions);
  }

  @Test
  void playUntilFinishedStopsAfterCurrentIterationWhenStopIsRequested() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4", "e7e5");
    MatchEngine matchEngine =
        new MatchEngine(stockfishClient, new ChessBoardService(), new GameProperties(250, 300));
    stockfishClient.onBestMove = () -> matchEngine.stopCurrentMatch();

    Match match = matchEngine.playUntilFinished();

    assertTrue(match.isInProgress());
    assertFalse(match.isFinished());
    assertEquals(1, match.moveCount());
    assertEquals("e2e4", match.moves().getFirst().notation().value());
  }

  @Test
  void startNewMatchRejectsReplacingActiveMatch() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient();
    MatchEngine matchEngine =
        new MatchEngine(stockfishClient, new ChessBoardService(), new GameProperties(250, 300));
    matchEngine.startNewMatch();

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::startNewMatch);

    assertEquals("Cannot start a new match while another match is in progress", error.getMessage());
  }

  @Test
  void currentMatchRejectsWhenNoMatchExists() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient();
    MatchEngine matchEngine =
        new MatchEngine(stockfishClient, new ChessBoardService(), new GameProperties(250, 300));

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::currentMatch);

    assertEquals("No match has been started", error.getMessage());
  }

  private static final class FakeStockfishClient implements StockfishClient {

    private final Deque<String> moves = new ArrayDeque<>();
    private final List<String> positions = new ArrayList<>();
    private final List<Duration> bestMoveCalls = new ArrayList<>();
    private int newGameCalls;
    private Runnable onBestMove;

    private FakeStockfishClient(String... moves) {
      for (String move : moves) {
        this.moves.addLast(move);
      }
    }

    @Override
    public void newGame() {
      newGameCalls++;
    }

    @Override
    public void setPosition(String fen) {
      positions.add(fen);
    }

    @Override
    public String bestMove(Duration thinkTime) {
      bestMoveCalls.add(thinkTime);
      if (onBestMove != null) {
        Runnable callback = onBestMove;
        onBestMove = null;
        callback.run();
      }
      String move = moves.pollFirst();
      if (move == null) {
        throw new IllegalStateException("No fake move configured");
      }
      return move;
    }

    @Override
    public void close() {}
  }
}
