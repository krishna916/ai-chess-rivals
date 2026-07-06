package dev.krishnamurti.ai_chess_rivals.chess.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
        ChessProperties.Stockfish stockfish = new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 16, 10);
        ChessProperties properties = new ChessProperties(stockfish);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void whenPathIsBlank_thenViolation() {
        ChessProperties.Stockfish stockfish = new ChessProperties.Stockfish("  ", 1, 16, 10);
        ChessProperties properties = new ChessProperties(stockfish);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish.path");
    }

    @Test
    void whenThreadsIsTooLow_thenViolation() {
        ChessProperties.Stockfish stockfish = new ChessProperties.Stockfish("stockfish/stockfish.exe", 0, 16, 10);
        ChessProperties properties = new ChessProperties(stockfish);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish.threads");
    }

    @Test
    void whenThreadsIsTooHigh_thenViolation() {
        ChessProperties.Stockfish stockfish = new ChessProperties.Stockfish("stockfish/stockfish.exe", 1025, 16, 10);
        ChessProperties properties = new ChessProperties(stockfish);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish.threads");
    }

    @Test
    void whenHashMbIsTooLow_thenViolation() {
        ChessProperties.Stockfish stockfish = new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 0, 10);
        ChessProperties properties = new ChessProperties(stockfish);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish.hashMb");
    }

    @Test
    void whenStartupTimeoutIsTooLow_thenViolation() {
        ChessProperties.Stockfish stockfish = new ChessProperties.Stockfish("stockfish/stockfish.exe", 1, 16, 0);
        ChessProperties properties = new ChessProperties(stockfish);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish.startupTimeoutSeconds");
    }

    @Test
    void whenStockfishIsNull_thenViolation() {
        ChessProperties properties = new ChessProperties(null);

        Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("stockfish");
    }
}
