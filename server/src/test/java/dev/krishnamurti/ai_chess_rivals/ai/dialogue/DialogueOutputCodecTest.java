package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import org.junit.jupiter.api.Test;

class DialogueOutputCodecTest {

  private final DialogueOutputCodec codec = new DialogueOutputCodec();

  @Test
  void parsesValidStructuredDialogue() {
    DialogueModelOutput output =
        codec.parse(
            "{\"text\":\"That loosened more than you think.\",\"emotion\":\"CALM\",\"reactionType\":\"MOVE_REACTION\"}");

    assertThat(output.text()).isEqualTo("That loosened more than you think.");
    assertThat(output.emotion().name()).isEqualTo("CALM");
    assertThat(output.reactionType()).isEqualTo(DialogueReactionType.MOVE_REACTION);
  }

  @Test
  void rejectsBlankOversizedUnknownWrongAndUnsafeOutput() {
    assertThat(codec.isValid(output(""), DialogueReactionType.MOVE_REACTION)).isFalse();
    assertThat(codec.isValid(output("x".repeat(281)), DialogueReactionType.MOVE_REACTION))
        .isFalse();
    assertThat(
            codec.isValid(
                "{\"text\":\"ok\",\"emotion\":\"UNKNOWN\",\"reactionType\":\"MOVE_REACTION\"}",
                DialogueReactionType.MOVE_REACTION))
        .isFalse();
    assertThat(
            codec.isValid(
                "{\"text\":\"ok\",\"emotion\":\"CALM\",\"reactionType\":\"UNKNOWN\"}",
                DialogueReactionType.MOVE_REACTION))
        .isFalse();
    assertThat(codec.isValid(output("ok"), DialogueReactionType.VICTORY)).isFalse();
    for (String unsafe : new String[] {"I will kill you", "suicide", "sexual", "worthless human"}) {
      assertThat(codec.isValid(output(unsafe), DialogueReactionType.MOVE_REACTION)).isFalse();
    }
    assertThat(codec.isValid("not json", DialogueReactionType.MOVE_REACTION)).isFalse();
  }

  private static String output(String text) {
    return "{\"text\":\"" + text + "\",\"emotion\":\"CALM\",\"reactionType\":\"MOVE_REACTION\"}";
  }
}
