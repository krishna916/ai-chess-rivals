package dev.krishnamurti.ai_chess_rivals.game.application;

/** Thrown when the backend match engine cannot continue a match safely. */
public class MatchEngineException extends RuntimeException {

  public MatchEngineException(String message) {
    super(message);
  }

  public MatchEngineException(String message, Throwable cause) {
    super(message, cause);
  }
}
