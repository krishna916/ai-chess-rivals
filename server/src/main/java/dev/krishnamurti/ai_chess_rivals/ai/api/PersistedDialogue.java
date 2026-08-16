package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PersistedDialogue(
    long id,
    UUID matchId,
    DialogueTriggerType triggerType,
    int triggerPly,
    String personalityKey,
    String personalityDisplayName,
    String text,
    DialogueEmotion emotion,
    DialogueReactionType reactionType,
    AiResponseSource source,
    Instant createdAt) {

  public PersistedDialogue {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    if (triggerPly < 0) {
      throw new IllegalArgumentException("triggerPly must not be negative");
    }
    requireText(personalityKey, "personalityKey");
    requireText(personalityDisplayName, "personalityDisplayName");
    requireText(text, "text");
    if (text.length() > 280) {
      throw new IllegalArgumentException("text must be at most 280 characters");
    }
    Objects.requireNonNull(emotion, "emotion must not be null");
    Objects.requireNonNull(reactionType, "reactionType must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
