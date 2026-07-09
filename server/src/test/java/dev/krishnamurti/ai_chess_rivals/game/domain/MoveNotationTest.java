package dev.krishnamurti.ai_chess_rivals.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoveNotationTest {

  @Test
  void rejectsNullNotation() {
    NullPointerException error =
        assertThrows(NullPointerException.class, () -> new MoveNotation(null));

    assertEquals("value must not be null", error.getMessage());
  }

  @Test
  void rejectsBlankNotation() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new MoveNotation(" "));

    assertEquals("value must not be blank", error.getMessage());
  }

  @Test
  void acceptsSimpleUciNotation() {
    MoveNotation notation = new MoveNotation("e2e4");

    assertEquals("e2e4", notation.value());
  }

  @Test
  void acceptsPromotionUciNotation() {
    MoveNotation notation = new MoveNotation("e7e8q");

    assertEquals("e7e8q", notation.value());
  }
}
