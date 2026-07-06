# Chess Properties Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Jakarta Bean Validation constraints to ChessProperties, specifically adding @NotBlank constraint to Stockfish path, and verify all validation rules with unit tests.

**Architecture:** Standard Bean Validation constraint annotations mapped to config fields. The class is annotated with `@Validated` so Spring Boot configuration processor validates configuration bindings at startup. A unit test validates constraints using standard `jakarta.validation.Validator`.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith, Jakarta Bean Validation (Hibernate Validator), JUnit Jupiter.

## Global Constraints
- Target File: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java`
- Keep changes minimal.
- Do not use `&&` to chain commands in PowerShell on Windows.

---

### Task 1: Update ChessProperties

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java`

**Interfaces:**
- Consumes: None.
- Produces: Updated `ChessProperties` and nested `Stockfish` record with `@NotBlank` validation on the `path` field.

- [ ] **Step 1: Write minimal implementation**

Edit `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java` to add `@NotBlank` to `path` in the `Stockfish` record and import `jakarta.validation.constraints.NotBlank`.

Replacement content:
```java
package dev.krishnamurti.ai_chess_rivals.chess.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration properties for the chess module.
 *
 * <p>Bound from the {@code app.chess} prefix in {@code application.yaml}.
 * Override individual properties via environment variables.
 *
 * <pre>
 * app:
 *   chess:
 *     stockfish:
 *       path: ${STOCKFISH_PATH:stockfish/stockfish}
 *       threads: 1
 *       hash-mb: 16
 *       startup-timeout-seconds: 10
 * </pre>
 *
 * @param stockfish Stockfish engine settings.
 */
@ConfigurationProperties(prefix = "app.chess")
@Validated
public record ChessProperties(@NotNull @Valid Stockfish stockfish) {

    /**
     * Stockfish engine settings.
     *
     * @param path                   Path to the Stockfish executable (absolute or relative to CWD).
     * @param threads                Number of search threads (UCI option "Threads"). Default: 1.
     * @param hashMb                 Hash table size in MB (UCI option "Hash"). Default: 16.
     * @param startupTimeoutSeconds  Reserved for future timeout enforcement. Not enforced in this version.
     */
    public record Stockfish(
            @NotBlank String path,
            @Min(1) @Max(1024) int threads,
            @Min(1) @Max(33554432) int hashMb,
            @Min(1) @Max(300) int startupTimeoutSeconds
    ) {}
}
```

- [ ] **Step 2: Commit**

Run:
```powershell
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessProperties.java
git commit -m "feat: add NotBlank validation on Stockfish path"
```

---

### Task 2: Create ChessPropertiesValidationTest

**Files:**
- Create: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessPropertiesValidationTest.java`

**Interfaces:**
- Consumes: Updated `ChessProperties` and nested `Stockfish` record.
- Produces: Test class verifying all Bean Validation constraints of `ChessProperties` are properly mapped.

- [ ] **Step 1: Write the validation tests**

Create the file `server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessPropertiesValidationTest.java` with tests checking both valid configurations and violation cases (blank path, threads out of range, hashMb out of range, startupTimeoutSeconds out of range, null stockfish).

Code content:
```java
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
```

- [ ] **Step 2: Run test to verify all tests pass**

Run: `.\mvnw test -Dtest=ChessPropertiesValidationTest` in the `server` folder.
Expected: Build success with all 7 tests passing.

- [ ] **Step 3: Commit**

Run:
```powershell
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/chess/config/ChessPropertiesValidationTest.java
git commit -m "test: add unit tests for ChessProperties validation"
```
