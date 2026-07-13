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
  void constructorRequiresResultWhenStatusIsFinished() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Match(
                    PlayerColor.WHITE,
                    BoardPosition.STARTING_POSITION,
                    List.of(),
                    GameStatus.FINISHED,
                    null));

    assertEquals("result is required when status is FINISHED", error.getMessage());
  }

  @Test
  void moveHistoryIsDefensivelyCopiedAndExposedImmutably() {
    List<Move> history = new ArrayList<>();
    history.add(
        new Move(
            1,
            PlayerColor.WHITE,
            new MoveNotation("e2e4"),
            new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
            quietPawnMoveDetails()));

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

  @Test
  void recordMoveAppendsMoveAndFlipsSideToMove() {
    Match match = Match.newGame();
    BoardPosition positionAfterMove =
        new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1");

    MoveDetails details = quietPawnMoveDetails();
    Match updatedMatch = match.recordMove(new MoveNotation("e2e4"), positionAfterMove, details);

    assertEquals(1, updatedMatch.moveCount());
    assertEquals(PlayerColor.BLACK, updatedMatch.sideToMove());
    assertEquals(positionAfterMove, updatedMatch.currentPosition());
    assertEquals(1, updatedMatch.moves().getFirst().sequenceNumber());
    assertEquals(PlayerColor.WHITE, updatedMatch.moves().getFirst().playedBy());
    assertEquals("e2e4", updatedMatch.moves().getFirst().notation().value());
    assertEquals(details, updatedMatch.moves().getFirst().details());
  }

  @Test
  void recordMoveRejectsFinishedMatch() {
    Match finishedMatch = Match.newGame().finish(GameResult.WHITE_WINS);

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                finishedMatch.recordMove(
                    new MoveNotation("e2e4"),
                    new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
                    quietPawnMoveDetails()));

    assertEquals("Cannot record a move when match is not in progress", error.getMessage());
  }

  @Test
  void finishSetsStatusAndResult() {
    Match finishedMatch = Match.newGame().finish(GameResult.DRAW);

    assertTrue(finishedMatch.isFinished());
    assertFalse(finishedMatch.isInProgress());
    assertEquals(GameStatus.FINISHED, finishedMatch.status());
    assertEquals(GameResult.DRAW, finishedMatch.result().orElseThrow());
  }

  private static Move historyMove() {
    return new Move(
        2,
        PlayerColor.BLACK,
        new MoveNotation("e7e5"),
        new BoardPosition("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"),
        new MoveDetails(
            ChessPieceType.PAWN,
            PlayerColor.BLACK,
            "e7",
            "e5",
            null,
            null,
            null,
            null,
            false,
            false));
  }

  private static MoveDetails quietPawnMoveDetails() {
    return new MoveDetails(
        ChessPieceType.PAWN, PlayerColor.WHITE, "e2", "e4", null, null, null, null, false, false);
  }
}
