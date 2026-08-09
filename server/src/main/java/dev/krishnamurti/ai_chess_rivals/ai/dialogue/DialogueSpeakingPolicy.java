package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import org.springframework.stereotype.Component;

@Component
final class DialogueSpeakingPolicy {

  static final double IMPORTANT_EVENT_PROBABILITY = 0.85;
  static final int SILENCE_PLY_THRESHOLD = 4;

  private final DoubleSupplier random;

  DialogueSpeakingPolicy() {
    this(ThreadLocalRandom.current()::nextDouble);
  }

  DialogueSpeakingPolicy(DoubleSupplier random) {
    this.random = Objects.requireNonNull(random, "random must not be null");
  }

  Optional<String> selectMoveSpeaker(
      DialogueMoveRequest request,
      PersonalityPromptProfile mover,
      PersonalityPromptProfile opponent) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(mover, "mover must not be null");
    Objects.requireNonNull(opponent, "opponent must not be null");

    String speakerKey = selectSpeaker(request);
    PersonalityPromptProfile speaker = speakerKey.equals(mover.key()) ? mover : opponent;

    if (isMandatory(request) || isRecentlySilent(request)) {
      return Optional.of(speakerKey);
    }

    double probability =
        request.capture() || request.check()
            ? IMPORTANT_EVENT_PROBABILITY
            : speaker.speakingProbability().doubleValue();
    return random.getAsDouble() < probability ? Optional.of(speakerKey) : Optional.empty();
  }

  private static String selectSpeaker(DialogueMoveRequest request) {
    if (request.checkmate() || request.promotion()) {
      return request.moverPersonalityKey();
    }
    if (request.evaluation().isPresent()) {
      EvaluationSwingClassification classification =
          request.evaluation().orElseThrow().classification();
      if (classification == EvaluationSwingClassification.MAJOR_MISTAKE) {
        return request.opponentPersonalityKey();
      }
      if (classification == EvaluationSwingClassification.MAJOR_GAIN) {
        return request.moverPersonalityKey();
      }
    }
    return request.check() ? request.opponentPersonalityKey() : request.moverPersonalityKey();
  }

  private static boolean isMandatory(DialogueMoveRequest request) {
    return request.checkmate()
        || request.promotion()
        || request
            .evaluation()
            .map(evaluation -> evaluation.classification() != EvaluationSwingClassification.STABLE)
            .orElse(false);
  }

  private static boolean isRecentlySilent(DialogueMoveRequest request) {
    int lastDialoguePly =
        request.recentDialogue().stream().mapToInt(line -> line.triggeringPly()).max().orElse(0);
    return request.ply() - lastDialoguePly >= SILENCE_PLY_THRESHOLD;
  }
}
