package dev.krishnamurti.ai_chess_rivals.game.web;

import java.util.Objects;

public record MatchPersonalityResponse(String key, String displayName) {
  public MatchPersonalityResponse {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(displayName, "displayName must not be null");
  }
}
