package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Objects;

public record GeneratedDialogue(
    String personalityKey,
    String text,
    DialogueEmotion emotion,
    DialogueReactionType reactionType,
    AiResponseSource source) {

  public GeneratedDialogue {
    if (personalityKey == null || personalityKey.isBlank()) {
      throw new IllegalArgumentException("personalityKey must not be blank");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("text must not be blank");
    }
    if (text.length() > 280) {
      throw new IllegalArgumentException("text must be at most 280 characters");
    }
    Objects.requireNonNull(emotion, "emotion must not be null");
    Objects.requireNonNull(reactionType, "reactionType must not be null");
    Objects.requireNonNull(source, "source must not be null");
  }
}
