package dev.krishnamurti.ai_chess_rivals.game.application;

public final class MatchNotFoundException extends RuntimeException {
  public MatchNotFoundException(String message) {
    super(message);
  }

  public MatchNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
