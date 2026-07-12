package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
final class RandomizedMatchPacing implements MatchPacing {

  private final long minimumMillis;
  private final long maximumMillis;
  private final RandomGenerator randomGenerator;
  private final Sleeper sleeper;

  @Autowired
  RandomizedMatchPacing(GameProperties gameProperties) {
    this(
        gameProperties.moveDelay().min(),
        gameProperties.moveDelay().max(),
        RandomGenerator.getDefault(),
        Thread::sleep);
  }

  RandomizedMatchPacing(
      Duration minimumDelay,
      Duration maximumDelay,
      RandomGenerator randomGenerator,
      Sleeper sleeper) {
    this.minimumMillis =
        Objects.requireNonNull(minimumDelay, "minimumDelay must not be null").toMillis();
    this.maximumMillis =
        Objects.requireNonNull(maximumDelay, "maximumDelay must not be null").toMillis();
    this.randomGenerator =
        Objects.requireNonNull(randomGenerator, "randomGenerator must not be null");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
  }

  @Override
  public void waitBeforeNextMove() throws InterruptedException {
    sleeper.sleep(selectDelayMillis());
  }

  private long selectDelayMillis() {
    if (minimumMillis == maximumMillis) {
      return minimumMillis;
    }
    return randomGenerator.nextLong(minimumMillis, Math.addExact(maximumMillis, 1));
  }
}

@FunctionalInterface
interface Sleeper {
  void sleep(long millis) throws InterruptedException;
}
