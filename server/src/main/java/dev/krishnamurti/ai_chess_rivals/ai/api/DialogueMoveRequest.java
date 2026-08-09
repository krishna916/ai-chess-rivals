package dev.krishnamurti.ai_chess_rivals.ai.api;

import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueMoveRequest(
    int ply,
    String moverPersonalityKey,
    String opponentPersonalityKey,
    String moveNotation,
    boolean capture,
    boolean check,
    boolean checkmate,
    boolean promotion,
    Optional<EvaluationSwing> evaluation,
    List<DialogueHistoryLine> recentDialogue) {

  public DialogueMoveRequest {
    if (ply <= 0) {
      throw new IllegalArgumentException("ply must be positive");
    }
    requireText(moverPersonalityKey, "moverPersonalityKey");
    requireText(opponentPersonalityKey, "opponentPersonalityKey");
    requireText(moveNotation, "moveNotation");
    if (moverPersonalityKey.equals(opponentPersonalityKey)) {
      throw new IllegalArgumentException("mover and opponent personalities must be distinct");
    }
    Objects.requireNonNull(evaluation, "evaluation must not be null");
    recentDialogue = copyHistory(recentDialogue);
  }

  @Override
  public List<DialogueHistoryLine> recentDialogue() {
    return List.copyOf(recentDialogue);
  }

  private static List<DialogueHistoryLine> copyHistory(List<DialogueHistoryLine> history) {
    Objects.requireNonNull(history, "recentDialogue must not be null");
    if (history.size() > 4) {
      throw new IllegalArgumentException("recentDialogue must contain at most four lines");
    }
    return List.copyOf(history);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
