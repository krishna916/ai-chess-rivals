package dev.krishnamurti.ai_chess_rivals.game.event;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveDetails;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import org.junit.jupiter.api.Test;

class MatchEventTest {

  @Test
  void matchStartedRejectsNullDomainValues() {
    assertThrows(
        NullPointerException.class, () -> new MatchStarted(null, BoardPosition.STARTING_POSITION));
    assertThrows(NullPointerException.class, () -> new MatchStarted(PlayerColor.WHITE, null));
  }

  @Test
  void movePlayedRejectsNullDomainValues() {
    assertThrows(
        NullPointerException.class,
        () ->
            new MovePlayed(
                1,
                null,
                new MoveNotation("e2e4"),
                BoardPosition.STARTING_POSITION,
                quietPawnMoveDetails()));
    assertThrows(
        NullPointerException.class,
        () ->
            new MovePlayed(
                1,
                PlayerColor.WHITE,
                null,
                BoardPosition.STARTING_POSITION,
                quietPawnMoveDetails()));
    assertThrows(
        NullPointerException.class,
        () ->
            new MovePlayed(
                1, PlayerColor.WHITE, new MoveNotation("e2e4"), null, quietPawnMoveDetails()));
    assertThrows(
        NullPointerException.class,
        () ->
            new MovePlayed(
                1,
                PlayerColor.WHITE,
                new MoveNotation("e2e4"),
                BoardPosition.STARTING_POSITION,
                null));
  }

  @Test
  void movePlayedRejectsNonPositivePly() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MovePlayed(
                0,
                PlayerColor.WHITE,
                new MoveNotation("e2e4"),
                BoardPosition.STARTING_POSITION,
                quietPawnMoveDetails()));
  }

  @Test
  void matchFinishedRejectsNullDomainValues() {
    assertThrows(
        NullPointerException.class,
        () -> new MatchFinished(null, BoardPosition.STARTING_POSITION, 1));
    assertThrows(NullPointerException.class, () -> new MatchFinished(GameResult.DRAW, null, 1));
  }

  @Test
  void matchFinishedRejectsNegativeTotalPlies() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MatchFinished(GameResult.DRAW, BoardPosition.STARTING_POSITION, -1));
  }

  private static MoveDetails quietPawnMoveDetails() {
    return new MoveDetails(
        ChessPieceType.PAWN, PlayerColor.WHITE, "e2", "e4", null, null, null, null, false, false);
  }
}
