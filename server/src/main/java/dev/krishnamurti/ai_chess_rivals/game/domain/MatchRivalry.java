package dev.krishnamurti.ai_chess_rivals.game.domain;

import java.util.Objects;

public record MatchRivalry(
    String whiteKey, String whiteDisplayName, String blackKey, String blackDisplayName) {

  public MatchRivalry {
    requireText(whiteKey, "whiteKey");
    requireText(whiteDisplayName, "whiteDisplayName");
    requireText(blackKey, "blackKey");
    requireText(blackDisplayName, "blackDisplayName");
    if (whiteKey.equals(blackKey)) {
      throw new IllegalArgumentException("White and Black personalities must be distinct");
    }
  }

  public String personalityKey(PlayerColor color) {
    Objects.requireNonNull(color, "color must not be null");
    return color == PlayerColor.WHITE ? whiteKey : blackKey;
  }

  public String displayName(PlayerColor color) {
    Objects.requireNonNull(color, "color must not be null");
    return color == PlayerColor.WHITE ? whiteDisplayName : blackDisplayName;
  }

  private static void requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
