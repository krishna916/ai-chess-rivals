package dev.krishnamurti.ai_chess_rivals.game.application;

public final class MatchCooldownException extends RuntimeException {

  private final long retryAfterSeconds;

  public MatchCooldownException(long retryAfterSeconds) {
    super("A new match cannot be started yet.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
