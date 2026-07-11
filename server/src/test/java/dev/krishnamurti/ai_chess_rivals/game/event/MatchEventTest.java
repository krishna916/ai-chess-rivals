package dev.krishnamurti.ai_chess_rivals.game.event;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
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
                false,
                false,
                false,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            new MovePlayed(
                1,
                PlayerColor.WHITE,
                null,
                BoardPosition.STARTING_POSITION,
                false,
                false,
                false,
                false));
    assertThrows(
        NullPointerException.class,
        () ->
            new MovePlayed(
                1,
                PlayerColor.WHITE,
                new MoveNotation("e2e4"),
                null,
                false,
                false,
                false,
                false));
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
                false,
                false,
                false,
                false));
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
}
