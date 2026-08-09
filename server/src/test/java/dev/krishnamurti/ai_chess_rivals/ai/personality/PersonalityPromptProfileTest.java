package dev.krishnamurti.ai_chess_rivals.ai.personality;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PersonalityPromptProfileTest {

  @Test
  void acceptsInclusiveProbabilityBoundariesAndFractionalValues() {
    assertThatCode(() -> profile("0")).doesNotThrowAnyException();
    assertThatCode(() -> profile("1")).doesNotThrowAnyException();
    assertThatCode(() -> profile("0.5")).doesNotThrowAnyException();
    assertThatCode(() -> profile("0.500")).doesNotThrowAnyException();
    assertThatCode(() -> profile("1.0")).doesNotThrowAnyException();
    assertThatCode(() -> profile("1.000")).doesNotThrowAnyException();
  }

  @Test
  void rejectsProbabilitiesOutsideInclusiveRange() {
    assertThatThrownBy(() -> profile("-0.001"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("speakingProbability must be between 0 and 1");
    assertThatThrownBy(() -> profile("1.001"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("speakingProbability must be between 0 and 1");
  }

  private static PersonalityPromptProfile profile(String probability) {
    return new PersonalityPromptProfile(
        "blaze", "Blaze", "Competitive traits", new BigDecimal(probability), "Dry style", "PG-13");
  }
}
