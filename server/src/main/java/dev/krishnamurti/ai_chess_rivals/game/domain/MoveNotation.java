package dev.krishnamurti.ai_chess_rivals.game.domain;

public record MoveNotation(String value) {

  public MoveNotation {
    if (value == null) {
      throw new NullPointerException("value must not be null");
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
