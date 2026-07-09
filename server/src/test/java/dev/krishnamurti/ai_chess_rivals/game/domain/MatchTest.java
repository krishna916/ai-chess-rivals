package dev.krishnamurti.ai_chess_rivals.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchTest {

  @Test
  void newGameReturnsExpectedInitialState() {
    Match match = Match.newGame();

    assertEquals(PlayerColor.WHITE, match.sideToMove());
    assertEquals(BoardPosition.STARTING_POSITION, match.currentPosition());
    assertEquals(GameStatus.IN_PROGRESS, match.status());
    assertTrue(match.isInProgress());
    assertFalse(match.isFinished());
    assertEquals(0, match.moveCount());
    assertTrue(match.moves().isEmpty());
    assertTrue(match.result().isEmpty());
  }

  @Test
  void constructorRejectsNullRequiredFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new Match(
                null, BoardPosition.STARTING_POSITION, List.of(), GameStatus.IN_PROGRESS, null));
    assertThrows(
        NullPointerException.class,
        () -> new Match(PlayerColor.WHITE, null, List.of(), GameStatus.IN_PROGRESS, null));
    assertThrows(
        NullPointerException.class,
        () ->
            new Match(
                PlayerColor.WHITE,
                BoardPosition.STARTING_POSITION,
                null,
                GameStatus.IN_PROGRESS,
                null));
    assertThrows(
        NullPointerException.class,
        () -> new Match(PlayerColor.WHITE, BoardPosition.STARTING_POSITION, List.of(), null, null));
  }

  @Test
  void constructorRejectsResultWhenStatusIsNotFinished() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Match(
                    PlayerColor.WHITE,
                    BoardPosition.STARTING_POSITION,
                    List.of(),
                    GameStatus.IN_PROGRESS,
                    GameResult.WHITE_WINS));

    assertEquals("result is only allowed when status is FINISHED", error.getMessage());
  }

  @Test
  void moveHistoryIsDefensivelyCopiedAndExposedImmutably() {
    List<Move> history = new ArrayList<>();
    history.add(
        new Move(
            1,
            PlayerColor.WHITE,
            new MoveNotation("e2e4"),
            new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")));

    Match match =
        new Match(
            PlayerColor.BLACK,
            new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
            history,
            GameStatus.IN_PROGRESS,
            null);

    history.clear();

    assertEquals(1, match.moveCount());
    assertThrows(UnsupportedOperationException.class, () -> match.moves().add(historyMove()));
  }

  private static Move historyMove() {
    return new Move(
        2,
        PlayerColor.BLACK,
        new MoveNotation("e7e5"),
        new BoardPosition("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"));
  }
}
