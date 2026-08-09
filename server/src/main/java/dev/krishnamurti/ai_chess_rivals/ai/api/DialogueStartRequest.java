package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.List;
import java.util.Objects;

public record DialogueStartRequest(
    String whitePersonalityKey,
    String blackPersonalityKey,
    List<DialogueHistoryLine> recentDialogue) {

  public DialogueStartRequest {
    requireText(whitePersonalityKey, "whitePersonalityKey");
    requireText(blackPersonalityKey, "blackPersonalityKey");
    if (whitePersonalityKey.equals(blackPersonalityKey)) {
      throw new IllegalArgumentException("white and black personalities must be distinct");
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
