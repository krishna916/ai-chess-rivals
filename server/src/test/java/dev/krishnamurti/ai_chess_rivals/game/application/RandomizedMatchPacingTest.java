package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RandomizedMatchPacingTest {

  @Test
  void selectsTheConfiguredLowerBound() throws InterruptedException {
    RecordingSleeper sleeper = new RecordingSleeper();
    MatchPacing pacing =
        pacing(
            Duration.ofMillis(3),
            Duration.ofMillis(10),
            new SequenceRandomGenerator(3L),
            sleeper);

    pacing.waitBeforeNextMove();

    assertThat(sleeper.delays).containsExactly(3L);
  }

  @Test
  void selectsTheConfiguredUpperBoundInclusively() throws InterruptedException {
    RecordingSleeper sleeper = new RecordingSleeper();
    MatchPacing pacing =
        pacing(
            Duration.ofMillis(3),
            Duration.ofMillis(10),
            new SequenceRandomGenerator(10L),
            sleeper);

    pacing.waitBeforeNextMove();

    assertThat(sleeper.delays).containsExactly(10L);
  }

  @Test
  void usesTheSameDelayWhenBoundsAreEqual() throws InterruptedException {
    RecordingSleeper sleeper = new RecordingSleeper();
    SequenceRandomGenerator randomGenerator = new SequenceRandomGenerator();
    MatchPacing pacing =
        pacing(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            randomGenerator,
            sleeper);

    pacing.waitBeforeNextMove();

    assertThat(sleeper.delays).containsExactly(1_000L);
    assertThat(randomGenerator.calls).isZero();
  }

  @Test
  void keepsTheZeroDelayCodePath() throws InterruptedException {
    RecordingSleeper sleeper = new RecordingSleeper();
    SequenceRandomGenerator randomGenerator = new SequenceRandomGenerator();
    MatchPacing pacing = pacing(Duration.ZERO, Duration.ZERO, randomGenerator, sleeper);

    pacing.waitBeforeNextMove();

    assertThat(sleeper.delays).containsExactly(0L);
    assertThat(randomGenerator.calls).isZero();
  }

  @Test
  void selectsAFreshDelayForEachInvocation() throws InterruptedException {
    RecordingSleeper sleeper = new RecordingSleeper();
    MatchPacing pacing =
        pacing(
            Duration.ofMillis(3),
            Duration.ofMillis(10),
            new SequenceRandomGenerator(4L, 8L),
            sleeper);

    pacing.waitBeforeNextMove();
    pacing.waitBeforeNextMove();

    assertThat(sleeper.delays).containsExactly(4L, 8L);
  }

  @Test
  void propagatesInterruptionFromSleeper() {
    Sleeper interruptedSleeper = millis -> {
      throw new InterruptedException("stop");
    };
    MatchPacing pacing =
        pacing(Duration.ZERO, Duration.ZERO, new SequenceRandomGenerator(), interruptedSleeper);

    assertThatThrownBy(pacing::waitBeforeNextMove).isInstanceOf(InterruptedException.class);
  }

  private static MatchPacing pacing(
      Duration minimumDelay,
      Duration maximumDelay,
      RandomGenerator randomGenerator,
      Sleeper sleeper) {
    return new RandomizedMatchPacing(minimumDelay, maximumDelay, randomGenerator, sleeper);
  }

  private static final class RecordingSleeper implements Sleeper {

    private final List<Long> delays = new ArrayList<>();

    @Override
    public void sleep(long millis) {
      delays.add(millis);
    }
  }

  private static final class SequenceRandomGenerator implements RandomGenerator {

    private final Deque<Long> values = new ArrayDeque<>();
    private int calls;

    private SequenceRandomGenerator(long... values) {
      for (long value : values) {
        this.values.addLast(value);
      }
    }

    @Override
    public long nextLong() {
      throw new UnsupportedOperationException("Unbounded nextLong is not used by these tests");
    }

    @Override
    public long nextLong(long bound) {
      calls++;
      long value = values.removeFirst();
      assertThat(value).isBetween(0L, bound - 1);
      return value;
    }

    @Override
    public long nextLong(long origin, long bound) {
      calls++;
      long value = values.removeFirst();
      assertThat(value).isBetween(origin, bound - 1);
      return value;
    }
  }
}
