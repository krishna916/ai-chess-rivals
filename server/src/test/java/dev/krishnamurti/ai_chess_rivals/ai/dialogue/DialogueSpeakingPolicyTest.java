package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DialogueSpeakingPolicyTest {

  private final PersonalityPromptProfile mover = profile("mover", "0.820");
  private final PersonalityPromptProfile opponent = profile("opponent", "0.360");

  @Test
  void mandatoryEventsAlwaysSpeakWithSemanticSpeaker() {
    DialogueSpeakingPolicy policy = new DialogueSpeakingPolicy(() -> 0.999);

    assertThat(
            policy.selectMoveSpeaker(
                request(true, false, false, Optional.empty()), mover, opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(false, false, true, Optional.empty()), mover, opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(
                    false,
                    false,
                    false,
                    Optional.of(
                        new EvaluationSwing(
                            0, 250, 250, EvaluationSwingClassification.MAJOR_GAIN))),
                mover,
                opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(
                    false,
                    false,
                    false,
                    Optional.of(
                        new EvaluationSwing(
                            0, -250, -250, EvaluationSwingClassification.MAJOR_MISTAKE))),
                mover,
                opponent))
        .contains("opponent");
  }

  @Test
  void usesStrictProbabilityBoundaries() {
    DialogueSpeakingPolicy importantSpeak = new DialogueSpeakingPolicy(() -> 0.849);
    DialogueSpeakingPolicy importantSilent = new DialogueSpeakingPolicy(() -> 0.850);
    DialogueSpeakingPolicy ordinarySpeak = new DialogueSpeakingPolicy(() -> 0.819);
    DialogueSpeakingPolicy ordinarySilent = new DialogueSpeakingPolicy(() -> 0.820);

    assertThat(
            importantSpeak.selectMoveSpeaker(
                request(false, true, false, Optional.empty()), mover, opponent))
        .contains("opponent");
    assertThat(
            importantSilent.selectMoveSpeaker(
                request(false, true, false, Optional.empty()), mover, opponent))
        .isEmpty();
    assertThat(
            ordinarySpeak.selectMoveSpeaker(
                request(false, false, false, Optional.empty()), mover, opponent))
        .contains("mover");
    assertThat(
            ordinarySilent.selectMoveSpeaker(
                request(false, false, false, Optional.empty()), mover, opponent))
        .isEmpty();
  }

  @Test
  void captureUsesImportantEventProbabilityAndMoverSpeaker() {
    DialogueMoveRequest capture =
        new DialogueMoveRequest(
            6,
            "mover",
            "opponent",
            "exd5",
            true,
            false,
            false,
            false,
            Optional.empty(),
            List.of(new DialogueHistoryLine(5, "opponent", "Opponent", "previous line")));
    DialogueSpeakingPolicy speak = new DialogueSpeakingPolicy(() -> 0.849);
    DialogueSpeakingPolicy silent = new DialogueSpeakingPolicy(() -> 0.850);

    assertThat(speak.selectMoveSpeaker(capture, mover, opponent)).contains("mover");
    assertThat(silent.selectMoveSpeaker(capture, mover, opponent)).isEmpty();
  }

  @Test
  void appliesSpeakerPrecedenceForMoveSemantics() {
    DialogueSpeakingPolicy policy = new DialogueSpeakingPolicy(() -> 0.0);

    assertThat(
            policy.selectMoveSpeaker(
                request(true, false, false, Optional.empty()), mover, opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(false, false, true, Optional.empty()), mover, opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(
                    false,
                    false,
                    false,
                    Optional.of(
                        new EvaluationSwing(
                            0, -250, -250, EvaluationSwingClassification.MAJOR_MISTAKE))),
                mover,
                opponent))
        .contains("opponent");
    assertThat(
            policy.selectMoveSpeaker(
                request(
                    false,
                    false,
                    false,
                    Optional.of(
                        new EvaluationSwing(
                            0, 250, 250, EvaluationSwingClassification.MAJOR_GAIN))),
                mover,
                opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(false, true, false, Optional.empty()), mover, opponent))
        .contains("opponent");
    assertThat(
            policy.selectMoveSpeaker(
                request(false, true, false, Optional.empty()), mover, opponent))
        .contains("opponent");
  }

  @Test
  void forcesSpeechAfterFourSilentPlies() {
    DialogueSpeakingPolicy policy = new DialogueSpeakingPolicy(() -> 0.999);

    assertThat(
            policy.selectMoveSpeaker(
                request(12, false, false, false, Optional.empty(), List.of(history(8))),
                mover,
                opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(12, false, false, false, Optional.empty(), List.of(history(9))),
                mover,
                opponent))
        .isEmpty();
    assertThat(
            policy.selectMoveSpeaker(
                request(4, false, false, false, Optional.empty(), List.of()), mover, opponent))
        .contains("mover");
    assertThat(
            policy.selectMoveSpeaker(
                request(3, false, false, false, Optional.empty(), List.of()), mover, opponent))
        .isEmpty();
  }

  private static DialogueMoveRequest request(
      boolean checkmate, boolean check, boolean promotion, Optional<EvaluationSwing> evaluation) {
    return request(1, checkmate, check, promotion, evaluation, List.of());
  }

  private static DialogueMoveRequest request(
      int ply,
      boolean checkmate,
      boolean check,
      boolean promotion,
      Optional<EvaluationSwing> evaluation,
      List<DialogueHistoryLine> history) {
    return new DialogueMoveRequest(
        ply, "mover", "opponent", "e4", false, check, checkmate, promotion, evaluation, history);
  }

  private static DialogueMoveRequest request(
      int ply,
      boolean checkmate,
      boolean check,
      boolean ignored,
      boolean promotion,
      Optional<EvaluationSwing> evaluation) {
    return request(ply, checkmate, check, promotion, evaluation, historyLines(ignored));
  }

  private static DialogueHistoryLine history(int ply) {
    return new DialogueHistoryLine(ply, "mover", "Mover", "previous line");
  }

  private static List<DialogueHistoryLine> historyLines(boolean included) {
    return included
        ? List.of(new DialogueHistoryLine(0, "mover", "Mover", "previous line"))
        : List.of();
  }

  private static PersonalityPromptProfile profile(String key, String probability) {
    return new PersonalityPromptProfile(
        key, key, "traits", new BigDecimal(probability), "style", "PG-13 boundaries");
  }
}
