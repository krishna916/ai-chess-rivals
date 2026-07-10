package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import org.junit.jupiter.api.Test;

class ChessBoardServiceTest {

  private final ChessBoardService chessBoardService = new ChessBoardService();

  @Test
  void applyMoveAdvancesStartingPosition() {
    BoardPosition nextPosition =
        chessBoardService.applyMove(BoardPosition.STARTING_POSITION, new MoveNotation("e2e4"));

    assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", nextPosition.fen());
  }

  @Test
  void applyMoveRejectsIllegalMove() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                chessBoardService.applyMove(
                    BoardPosition.STARTING_POSITION, new MoveNotation("e2e5")));

    assertEquals("Illegal move for current position: e2e5", error.getMessage());
  }

  @Test
  void determineResultReturnsWinnerForCheckmate() {
    BoardPosition checkmatePosition = new BoardPosition("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1");

    assertEquals(
        GameResult.WHITE_WINS,
        chessBoardService.determineResult(checkmatePosition, PlayerColor.BLACK).orElseThrow());
    assertTrue(chessBoardService.isFinished(checkmatePosition, PlayerColor.BLACK));
  }

  @Test
  void determineResultReturnsDrawForStalemate() {
    BoardPosition stalematePosition = new BoardPosition("7k/5Q2/7K/8/8/8/8/8 b - - 0 1");

    assertEquals(
        GameResult.DRAW,
        chessBoardService.determineResult(stalematePosition, PlayerColor.BLACK).orElseThrow());
  }

  @Test
  void determineResultReturnsDrawForInsufficientMaterial() {
    BoardPosition drawPosition = new BoardPosition("8/8/8/8/8/5k2/8/4K3 w - - 0 1");

    assertEquals(
        GameResult.DRAW,
        chessBoardService.determineResult(drawPosition, PlayerColor.WHITE).orElseThrow());
  }

  @Test
  void determineResultReturnsEmptyWhenGameIsNotFinished() {
    assertTrue(
        chessBoardService
            .determineResult(BoardPosition.STARTING_POSITION, PlayerColor.WHITE)
            .isEmpty());
    assertFalse(chessBoardService.isFinished(BoardPosition.STARTING_POSITION, PlayerColor.WHITE));
  }
}
