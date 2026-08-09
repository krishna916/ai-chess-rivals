package dev.krishnamurti.ai_chess_rivals.ai.api;

public record DialogueHistoryLine(
    int triggeringPly, String speakerKey, String speakerDisplayName, String text) {

  public DialogueHistoryLine {
    if (triggeringPly < 0) {
      throw new IllegalArgumentException("triggeringPly must not be negative");
    }
    requireText(speakerKey, "speakerKey");
    requireText(speakerDisplayName, "speakerDisplayName");
    requireText(text, "text");
    if (text.length() > 280) {
      throw new IllegalArgumentException("text must be at most 280 characters");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
