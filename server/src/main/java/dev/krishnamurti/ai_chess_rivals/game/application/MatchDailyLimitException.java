package dev.krishnamurti.ai_chess_rivals.game.application;

public final class MatchDailyLimitException extends RuntimeException {

  private final int dailyStartLimit;

  public MatchDailyLimitException(int dailyStartLimit) {
    super("The configured daily match limit has been reached.");
    this.dailyStartLimit = dailyStartLimit;
  }

  public int dailyStartLimit() {
    return dailyStartLimit;
  }
}
