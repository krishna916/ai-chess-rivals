package dev.krishnamurti.ai_chess_rivals.chess.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChessPropertiesValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void whenPropertiesAreValid_thenNoViolations() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 16, 10, 30, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).isEmpty();
  }

  @Test
  void whenPathIsBlank_thenViolation() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("  ", 1, 16, 10, 30, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("stockfish.path");
  }

  @Test
  void whenThreadsIsTooLow_thenViolation() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("stockfish/stockfish.exe", 0, 16, 10, 30, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("stockfish.threads");
  }

  @Test
  void whenThreadsIsTooHigh_thenViolation() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("stockfish/stockfish.exe", 1025, 16, 10, 30, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("stockfish.threads");
  }

  @Test
  void whenHashMbIsTooLow_thenViolation() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 0, 10, 30, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("stockfish.hashMb");
  }

  @Test
  void whenStartupTimeoutIsTooLow_thenViolation() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 16, 0, 30, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("stockfish.startupTimeoutSeconds");
  }

  @Test
  void whenMoveTimeoutIsTooLow_thenViolation() {
    ChessProperties.Stockfish stockfish =
        new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 16, 10, 0, evaluation());
    ChessProperties properties = new ChessProperties(stockfish);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString())
        .isEqualTo("stockfish.moveTimeoutSeconds");
  }

  @Test
  void whenStockfishIsNull_thenViolation() {
    ChessProperties properties = new ChessProperties(null);

    Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish");
  }

  @Test
  void whenEvaluationDepthIsTooLow_thenViolation() {
    assertThat(violationsFor(new ChessProperties.Stockfish("path", 1, 16, 10, 30, evaluation(0))))
        .extracting(ConstraintViolation::getPropertyPath)
        .anyMatch(path -> path.toString().equals("stockfish.evaluation.depth"));
  }

  @Test
  void whenEvaluationMoveTimeIsTooLow_thenViolation() {
    ChessProperties.Stockfish.Evaluation invalid = new ChessProperties.Stockfish.Evaluation(8, 0, 200, 200);
    assertThat(violationsFor(new ChessProperties.Stockfish("path", 1, 16, 10, 30, invalid)))
        .extracting(ConstraintViolation::getPropertyPath)
        .anyMatch(path -> path.toString().equals("stockfish.evaluation.moveTimeMillis"));
  }

  @Test
  void whenMajorGainThresholdIsTooLow_thenViolation() {
    ChessProperties.Stockfish.Evaluation invalid = new ChessProperties.Stockfish.Evaluation(8, 50, 0, 200);
    assertThat(violationsFor(new ChessProperties.Stockfish("path", 1, 16, 10, 30, invalid)))
        .extracting(ConstraintViolation::getPropertyPath)
        .anyMatch(path -> path.toString().equals("stockfish.evaluation.majorGainThresholdCentipawns"));
  }

  @Test
  void whenMajorMistakeThresholdIsTooLow_thenViolation() {
    ChessProperties.Stockfish.Evaluation invalid = new ChessProperties.Stockfish.Evaluation(8, 50, 200, 0);
    assertThat(violationsFor(new ChessProperties.Stockfish("path", 1, 16, 10, 30, invalid)))
        .extracting(ConstraintViolation::getPropertyPath)
        .anyMatch(path -> path.toString().equals("stockfish.evaluation.majorMistakeThresholdCentipawns"));
  }

  @Test
  void whenEvaluationIsNull_thenViolation() {
    Set<ConstraintViolation<ChessProperties>> violations =
        validator.validate(new ChessProperties(new ChessProperties.Stockfish("path", 1, 16, 10, 30, null)));

    assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
        .anyMatch(path -> path.toString().equals("stockfish.evaluation"));
  }

  private static ChessProperties.Stockfish.Evaluation evaluation() {
    return evaluation(8);
  }

  private static ChessProperties.Stockfish.Evaluation evaluation(int depth) {
    return new ChessProperties.Stockfish.Evaluation(depth, 50, 200, 200);
  }

  private Set<ConstraintViolation<ChessProperties>> violationsFor(
      ChessProperties.Stockfish stockfish) {
    return validator.validate(new ChessProperties(stockfish));
  }
}
