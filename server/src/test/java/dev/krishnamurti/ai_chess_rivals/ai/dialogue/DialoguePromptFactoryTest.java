package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueStartRequest;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DialoguePromptFactoryTest {

  private final DialoguePromptFactory factory = new DialoguePromptFactory();
  private final PersonalityPromptProfile speaker = profile("blaze", "Blaze");
  private final PersonalityPromptProfile opponent = profile("vesper", "Vesper");

  @Test
  void rendersMovePromptWithMinimalContextAndAllHistory() {
    List<DialogueHistoryLine> history =
        List.of(
            new DialogueHistoryLine(1, "blaze", "Blaze", "first line"),
            new DialogueHistoryLine(2, "vesper", "Vesper", "second line"),
            new DialogueHistoryLine(3, "blaze", "Blaze", "third line"),
            new DialogueHistoryLine(4, "vesper", "Vesper", "fourth line"));
    DialogueMoveRequest request =
        new DialogueMoveRequest(
            5,
            "blaze",
            "vesper",
            "Qxd5+",
            true,
            true,
            false,
            false,
            Optional.of(
                new EvaluationSwing(100, 350, 250, EvaluationSwingClassification.MAJOR_GAIN)),
            history);

    String prompt =
        factory.movePrompt(
            request, speaker, opponent, DialogueReactionType.MOVE_REACTION, "FORMAT");

    assertThat(prompt)
        .contains("Blaze", "Competitive", "Dry", "PG-13", "Vesper")
        .contains(
            "Ply: 5",
            "Move: Qxd5+",
            "capture=true",
            "check=true",
            "checkmate=false",
            "promotion=false")
        .contains("before=100cp, after=350cp, swing=250cp, classification=MAJOR_GAIN")
        .contains("first line", "second line", "third line", "fourth line")
        .contains("Recent dialogue is optional context")
        .contains("Never calculate, choose, validate, or recommend a chess move")
        .contains("MOVE_REACTION", "FORMAT");
  }

  @Test
  void rendersUnavailableEvaluationAndAllTemplateVariants() {
    DialogueMoveRequest move =
        new DialogueMoveRequest(
            1, "blaze", "vesper", "e4", false, false, false, false, Optional.empty(), List.of());
    String movePrompt =
        factory.movePrompt(move, speaker, opponent, DialogueReactionType.MOVE_REACTION, "FORMAT");
    assertThat(movePrompt).contains("Evaluation: unavailable").contains("Recent dialogue: none");

    String startPrompt =
        factory.startPrompt(
            new DialogueStartRequest("blaze", "vesper", List.of()), speaker, opponent, "FORMAT");
    assertThat(startPrompt).contains("GAME_START", "Blaze", "Vesper", "FORMAT");

    DialogueEndRequest decisive =
        new DialogueEndRequest(
            "blaze", DialogueOutcome.VICTORY, "vesper", DialogueOutcome.DEFEAT, 20, List.of());
    String endPrompt =
        factory.endPrompt(
            decisive,
            speaker,
            opponent,
            DialogueOutcome.VICTORY,
            DialogueReactionType.VICTORY,
            "FORMAT");
    assertThat(endPrompt).contains("VICTORY", "winner", "Blaze", "Vesper");

    DialogueEndRequest draw =
        new DialogueEndRequest(
            "blaze", DialogueOutcome.DRAW, "vesper", DialogueOutcome.DRAW, 20, List.of());
    String drawPrompt =
        factory.endPrompt(
            draw, speaker, opponent, DialogueOutcome.DRAW, DialogueReactionType.DRAW, "FORMAT");
    assertThat(drawPrompt).contains("DRAW", "draw", "FORMAT");
  }

  private static PersonalityPromptProfile profile(String key, String name) {
    return new PersonalityPromptProfile(
        key, name, "Competitive traits", new BigDecimal("0.820"), "Dry style", "PG-13 boundary");
  }
}
