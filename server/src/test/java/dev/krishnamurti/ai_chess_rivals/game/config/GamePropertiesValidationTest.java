package dev.krishnamurti.ai_chess_rivals.game.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GamePropertiesValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void acceptsZeroDelayRange() {
    assertThat(validator.validate(properties(Duration.ZERO, Duration.ZERO))).isEmpty();
  }

  @Test
  void acceptsPositiveDelayRange() {
    assertThat(validator.validate(properties(Duration.ofSeconds(3), Duration.ofSeconds(10))))
        .isEmpty();
  }

  @Test
  void acceptsEqualNonZeroDelayRange() {
    assertThat(validator.validate(properties(Duration.ofSeconds(3), Duration.ofSeconds(3))))
        .isEmpty();
  }

  @Test
  void rejectsNegativeMinimumDelay() {
    assertSingleViolation(
        properties(Duration.ofMillis(-1), Duration.ZERO), "moveDelay.minimumNonNegative");
  }

  @Test
  void rejectsNegativeMaximumDelay() {
    assertSingleViolation(
        properties(Duration.ZERO, Duration.ofMillis(-1)), "moveDelay.maximumNonNegative");
  }

  @Test
  void rejectsMinimumDelayGreaterThanMaximum() {
    assertSingleViolation(
        properties(Duration.ofSeconds(10), Duration.ofSeconds(3)), "moveDelay.validRange");
  }

  @Test
  void rejectsMissingMoveDelay() {
    assertSingleViolation(new GameProperties(250, 300, null), "moveDelay");
  }

  private static GameProperties properties(Duration min, Duration max) {
    return new GameProperties(250, 300, new GameProperties.MoveDelay(min, max));
  }

  private static void assertSingleViolation(GameProperties properties, String propertyPath) {
    Set<ConstraintViolation<GameProperties>> violations = validator.validate(properties);

    assertThat(violations).singleElement();
    assertThat(violations.iterator().next().getPropertyPath()).hasToString(propertyPath);
  }
}
