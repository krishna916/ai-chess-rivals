package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class MatchExecutionGuardTest {

  @Test
  void onlyOneConcurrentReservationCanRun() throws Exception {
    MatchExecutionGuard guard = guard(Duration.ofSeconds(60), 12);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Object> first = executor.submit(() -> reserveAfterSignal(guard, ready, start));
      Future<Object> second = executor.submit(() -> reserveAfterSignal(guard, ready, start));
      ready.await();
      start.countDown();

      Object firstResult = getResult(first);
      Object secondResult = getResult(second);
      assertEquals(1, countReservations(firstResult, secondResult));
      assertEquals(1, countConflicts(firstResult, secondResult));
      assertEquals(1, guard.availability().dailyStartsAccepted());
    }
  }

  @Test
  void successfulFinishStartsCooldownAndRoundsRetryUpConsistently() {
    MutableClock clock = clockAt("2026-07-13T12:00:00Z");
    MatchExecutionGuard guard = new MatchExecutionGuard(clock, Duration.ofSeconds(60), 12);
    guard.reserveStart();
    guard.markFinished();
    clock.advance(Duration.ofMillis(900));

    MatchCooldownException exception =
        assertThrows(MatchCooldownException.class, guard::reserveStart);
    MatchStartAvailability availability = guard.availability();

    assertEquals(60L, exception.retryAfterSeconds());
    assertEquals(60L, availability.retryAfterSeconds());
    assertEquals(MatchStartBlockReason.MATCH_COOLDOWN_ACTIVE, availability.blockedBy());
  }

  @Test
  void repeatedStopDoesNotExtendCooldownAndBoundaryIsInclusive() {
    MutableClock clock = clockAt("2026-07-13T12:00:00Z");
    MatchExecutionGuard guard = new MatchExecutionGuard(clock, Duration.ofSeconds(60), 12);
    guard.reserveStart();

    assertTrue(guard.markStopped());
    clock.advance(Duration.ofSeconds(30));
    assertFalse(guard.markStopped());
    clock.advance(Duration.ofSeconds(30));

    assertDoesNotThrow(guard::reserveStart);
  }

  @Test
  void abortedReservationDoesNotCountAgainstDailyLimit() {
    MatchExecutionGuard guard = guard(Duration.ZERO, 1);
    MatchExecutionGuard.StartReservation reservation = guard.reserveStart();
    guard.abortStart(reservation);

    assertEquals(0, guard.availability().dailyStartsAccepted());
    assertDoesNotThrow(guard::reserveStart);
  }

  @Test
  void thirteenthAcceptedStartIsRejected() {
    MatchExecutionGuard guard = guard(Duration.ZERO, 12);
    for (int index = 0; index < 12; index++) {
      guard.reserveStart();
      guard.markStopped();
    }

    MatchDailyLimitException exception =
        assertThrows(MatchDailyLimitException.class, guard::reserveStart);

    assertEquals(12, exception.dailyStartLimit());
    assertEquals(12, guard.availability().dailyStartsAccepted());
    assertEquals(MatchStartBlockReason.MATCH_DAILY_LIMIT_REACHED, guard.availability().blockedBy());
  }

  @Test
  void utcMidnightResetsAcceptedStartCounter() {
    MutableClock clock = clockAt("2026-07-13T23:59:59Z");
    MatchExecutionGuard guard = new MatchExecutionGuard(clock, Duration.ZERO, 1);
    guard.reserveStart();
    guard.markStopped();
    assertThrows(MatchDailyLimitException.class, guard::reserveStart);

    clock.advance(Duration.ofSeconds(1));

    assertDoesNotThrow(guard::reserveStart);
    assertEquals(1, guard.availability().dailyStartsAccepted());
  }

  @Test
  void backgroundAbortClearsRunningWithoutStartingCooldown() {
    MatchExecutionGuard guard = guard(Duration.ofSeconds(60), 12);
    guard.reserveStart();

    guard.abortExecution();

    assertFalse(guard.isRunning());
    assertTrue(guard.availability().allowed());
  }

  private static MatchExecutionGuard guard(Duration cooldown, int limit) {
    return new MatchExecutionGuard(clockAt("2026-07-13T12:00:00Z"), cooldown, limit);
  }

  private static Object reserveAfterSignal(
      MatchExecutionGuard guard, CountDownLatch ready, CountDownLatch start) throws Exception {
    ready.countDown();
    start.await();
    try {
      return guard.reserveStart();
    } catch (MatchConflictException exception) {
      return exception;
    }
  }

  private static Object getResult(Future<Object> future)
      throws InterruptedException, ExecutionException {
    return future.get();
  }

  private static long countReservations(Object... results) {
    return java.util.Arrays.stream(results)
        .filter(MatchExecutionGuard.StartReservation.class::isInstance)
        .count();
  }

  private static long countConflicts(Object... results) {
    return java.util.Arrays.stream(results)
        .filter(MatchConflictException.class::isInstance)
        .count();
  }

  private static MutableClock clockAt(String instant) {
    return new MutableClock(Instant.parse(instant));
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      if (!ZoneOffset.UTC.equals(zone)) {
        throw new IllegalArgumentException("Only UTC is supported");
      }
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
