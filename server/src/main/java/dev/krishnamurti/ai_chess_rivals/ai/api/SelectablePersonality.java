package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Objects;

public record SelectablePersonality(String key, String displayName) {
  public SelectablePersonality {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(displayName, "displayName must not be null");
    if (key.isBlank()) {
      throw new IllegalArgumentException("key must not be blank");
    }
    if (displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
  }
}
