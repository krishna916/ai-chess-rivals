package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockfishPlayerTest {

  private static GameProperties gameProperties(int moveThinkTimeMillis, int maxPlies) {
    return new GameProperties(
        moveThinkTimeMillis, maxPlies, new GameProperties.MoveDelay(Duration.ZERO, Duration.ZERO));
  }

  @Test
  void startNewGameDelegatesToStockfish() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4");
    StockfishPlayer player = new StockfishPlayer(stockfishClient, gameProperties(250, 300));

    player.startNewGame();

    assertEquals(1, stockfishClient.newGameCalls);
  }

  @Test
  void chooseMovePassesCurrentFenToStockfish() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4");
    StockfishPlayer player = new StockfishPlayer(stockfishClient, gameProperties(250, 300));
    Match match = Match.newGame();

    player.chooseMove(match);

    assertEquals(
        List.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
        stockfishClient.positions);
  }

  @Test
  void chooseMoveUsesConfiguredThinkTime() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4");
    StockfishPlayer player = new StockfishPlayer(stockfishClient, gameProperties(375, 300));

    player.chooseMove(Match.newGame());

    assertEquals(List.of(Duration.ofMillis(375)), stockfishClient.bestMoveCalls);
  }

  @Test
  void chooseMoveWrapsReturnedUciMoveAsMoveNotation() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("g1f3");
    StockfishPlayer player = new StockfishPlayer(stockfishClient, gameProperties(250, 300));

    MoveNotation moveNotation = player.chooseMove(Match.newGame());

    assertEquals(new MoveNotation("g1f3"), moveNotation);
  }

  @Test
  void chooseMoveRejectsNullMatch() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4");
    StockfishPlayer player = new StockfishPlayer(stockfishClient, gameProperties(250, 300));

    NullPointerException error =
        assertThrows(NullPointerException.class, () -> player.chooseMove(null));

    assertEquals("match must not be null", error.getMessage());
  }

  @Test
  void chooseMoveRejectsFinishedMatch() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4");
    StockfishPlayer player = new StockfishPlayer(stockfishClient, gameProperties(250, 300));
    Match finishedMatch = Match.newGame().finish(GameResult.DRAW);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> player.chooseMove(finishedMatch));

    assertEquals("Cannot choose a move when match is not in progress", error.getMessage());
  }

  @Test
  void stockfishPlayerImplementsChessPlayerContract() {
    FakeStockfishClient stockfishClient = new FakeStockfishClient("e2e4");

    Object player = new StockfishPlayer(stockfishClient, gameProperties(250, 300));

    assertInstanceOf(ChessPlayer.class, player);
  }

  private static final class FakeStockfishClient implements StockfishClient {

    private final List<String> moves = new ArrayList<>();
    private final List<String> positions = new ArrayList<>();
    private final List<Duration> bestMoveCalls = new ArrayList<>();
    private int newGameCalls;

    private FakeStockfishClient(String... moves) {
      this.moves.addAll(List.of(moves));
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
      if (moves.isEmpty()) {
        throw new IllegalStateException("No fake move configured");
      }
      return moves.removeFirst();
    }

    @Override
    public void close() {}
  }
}
