package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.List;
import java.util.Objects;

public record DialogueEndRequest(
    String whitePersonalityKey,
    DialogueOutcome whiteOutcome,
    String blackPersonalityKey,
    DialogueOutcome blackOutcome,
    int totalPlies,
    List<DialogueHistoryLine> recentDialogue) {

  public DialogueEndRequest {
    requireText(whitePersonalityKey, "whitePersonalityKey");
    requireText(blackPersonalityKey, "blackPersonalityKey");
    if (whitePersonalityKey.equals(blackPersonalityKey)) {
      throw new IllegalArgumentException("white and black personalities must be distinct");
    }
    Objects.requireNonNull(whiteOutcome, "whiteOutcome must not be null");
    Objects.requireNonNull(blackOutcome, "blackOutcome must not be null");
    if (totalPlies < 0) {
      throw new IllegalArgumentException("totalPlies must not be negative");
    }
    boolean validPair =
        (whiteOutcome == DialogueOutcome.VICTORY && blackOutcome == DialogueOutcome.DEFEAT)
            || (whiteOutcome == DialogueOutcome.DEFEAT && blackOutcome == DialogueOutcome.VICTORY)
            || (whiteOutcome == DialogueOutcome.DRAW && blackOutcome == DialogueOutcome.DRAW);
    if (!validPair) {
      throw new IllegalArgumentException("end outcomes must be victory/defeat or draw/draw");
    }
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
