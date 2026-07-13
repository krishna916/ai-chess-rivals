package dev.krishnamurti.ai_chess_rivals.game.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Owns all in-memory state that decides whether a match may execute. */
public final class MatchExecutionGuard {

  private final Clock clock;
  private final Duration cooldown;
  private final int dailyStartLimit;

  private boolean running;
  private Instant nextStartAllowedAt = Instant.MIN;
  private LocalDate countedDate;
  private int acceptedStarts;

  public MatchExecutionGuard(Clock clock, Duration cooldown, int dailyStartLimit) {
    this.clock = clock;
    this.cooldown = cooldown;
    this.dailyStartLimit = dailyStartLimit;
  }

  public synchronized StartReservation reserveStart() {
    resetCounterForCurrentUtcDate();
    if (running) {
      throw new MatchConflictException("A match is already active.");
    }

    Instant now = clock.instant();
    if (now.isBefore(nextStartAllowedAt)) {
      throw new MatchCooldownException(retryAfterSeconds(now));
    }
    if (acceptedStarts >= dailyStartLimit) {
      throw new MatchDailyLimitException(dailyStartLimit);
    }

    running = true;
    acceptedStarts++;
    return new StartReservation(countedDate);
  }

  public synchronized void abortStart(StartReservation reservation) {
    if (!running) {
      return;
    }
    running = false;
    if (reservation.countedDate().equals(countedDate) && acceptedStarts > 0) {
      acceptedStarts--;
    }
  }

  public synchronized void markFinished() {
    if (running) {
      running = false;
      startCooldown();
    }
  }

  public synchronized boolean markStopped() {
    if (!running) {
      return false;
    }
    running = false;
    startCooldown();
    return true;
  }

  public synchronized void abortExecution() {
    running = false;
  }

  public synchronized boolean isRunning() {
    return running;
  }

  public synchronized MatchStartAvailability availability() {
    resetCounterForCurrentUtcDate();
    Instant now = clock.instant();
    if (running) {
      return unavailable(MatchStartBlockReason.MATCH_ALREADY_RUNNING, 0);
    }
    if (now.isBefore(nextStartAllowedAt)) {
      return unavailable(MatchStartBlockReason.MATCH_COOLDOWN_ACTIVE, retryAfterSeconds(now));
    }
    if (acceptedStarts >= dailyStartLimit) {
      return unavailable(MatchStartBlockReason.MATCH_DAILY_LIMIT_REACHED, 0);
    }
    return new MatchStartAvailability(true, null, 0, acceptedStarts, dailyStartLimit);
  }

  private MatchStartAvailability unavailable(MatchStartBlockReason reason, long retryAfterSeconds) {
    return new MatchStartAvailability(
        false, reason, retryAfterSeconds, acceptedStarts, dailyStartLimit);
  }

  private void startCooldown() {
    nextStartAllowedAt = clock.instant().plus(cooldown);
  }

  private long retryAfterSeconds(Instant now) {
    long remainingMillis = Duration.between(now, nextStartAllowedAt).toMillis();
    return Math.max(1L, (remainingMillis + 999L) / 1_000L);
  }

  private void resetCounterForCurrentUtcDate() {
    LocalDate currentDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    if (!currentDate.equals(countedDate)) {
      countedDate = currentDate;
      acceptedStarts = 0;
    }
  }

  public record StartReservation(LocalDate countedDate) {}
}
