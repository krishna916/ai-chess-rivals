package dev.krishnamurti.ai_chess_rivals.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoveTest {

  @Test
  void rejectsZeroSequenceNumber() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Move(
                    0,
                    PlayerColor.WHITE,
                    new MoveNotation("e2e4"),
                    new BoardPosition(
                        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")));

    assertEquals("sequenceNumber must be positive", error.getMessage());
  }

  @Test
  void rejectsNegativeSequenceNumber() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new Move(
                    -1,
                    PlayerColor.WHITE,
                    new MoveNotation("e2e4"),
                    new BoardPosition(
                        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1")));

    assertEquals("sequenceNumber must be positive", error.getMessage());
  }

  @Test
  void acceptsValidMoveData() {
    Move move =
        new Move(
            1,
            PlayerColor.WHITE,
            new MoveNotation("e2e4"),
            new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"));

    assertEquals(1, move.sequenceNumber());
    assertEquals(PlayerColor.WHITE, move.playedBy());
    assertEquals("e2e4", move.notation().value());
  }
}
