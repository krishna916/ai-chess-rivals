package dev.krishnamurti.ai_chess_rivals.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BoardPositionTest {

  @Test
  void rejectsNullFen() {
    NullPointerException error =
        assertThrows(NullPointerException.class, () -> new BoardPosition(null));

    assertEquals("fen must not be null", error.getMessage());
  }

  @Test
  void rejectsBlankFen() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new BoardPosition("   "));

    assertEquals("fen must not be blank", error.getMessage());
  }

  @Test
  void acceptsStandardStartingFen() {
    BoardPosition position = new BoardPosition(BoardPosition.STARTING_POSITION.fen());

    assertEquals(BoardPosition.STARTING_POSITION, position);
  }
}
