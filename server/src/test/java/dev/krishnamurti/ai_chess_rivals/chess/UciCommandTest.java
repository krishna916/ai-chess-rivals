package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UciCommandTest {

  @Test
  void evaluateUsesBothDepthAndMoveTimeBounds() {
    assertThat(UciCommand.evaluate(8, 50).text()).isEqualTo("go depth 8 movetime 50");
  }
}
