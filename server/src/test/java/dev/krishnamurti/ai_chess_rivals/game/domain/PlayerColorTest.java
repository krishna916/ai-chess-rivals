package dev.krishnamurti.ai_chess_rivals.game.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlayerColorTest {

  @Test
  void whiteOppositeIsBlack() {
    assertEquals(PlayerColor.BLACK, PlayerColor.WHITE.opposite());
  }

  @Test
  void blackOppositeIsWhite() {
    assertEquals(PlayerColor.WHITE, PlayerColor.BLACK.opposite());
  }
}
