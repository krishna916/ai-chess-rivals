# Design Spec: Chess Properties Validation

**Date**: 2026-07-06  
**Status**: APPROVED

## Purpose
Ensure all configuration values bound to `app.chess` in `application.yaml` or env vars are strictly validated at startup/binding time to prevent invalid settings from propagating to the Stockfish engine client.

## Scope of Changes
- Add Jakarta Bean Validation annotations to the nested `Stockfish` record in `ChessProperties.java`, ensuring all fields (including `path`) are constrained appropriately.
- Add a new unit test suite `ChessPropertiesValidationTest` that runs independently of Spring Boot Context (no database required) to verify each constraint.

## Proposed Changes

### 1. `ChessProperties.java`
Update `Stockfish` record to add `@NotBlank` to `path`. The full validation annotations on the record fields will be:
- `@NotBlank String path`
- `@Min(1) @Max(1024) int threads`
- `@Min(1) @Max(33554432) int hashMb`
- `@Min(1) @Max(300) int startupTimeoutSeconds`

### 2. `ChessPropertiesValidationTest.java`
Create a test class under `dev.krishnamurti.ai_chess_rivals.chess.config` package in the test directory:
- Use standard `jakarta.validation.Validator` to perform validation assertions.
- Validate scenarios:
  - Valid configuration produces zero violations.
  - Blank/empty `path` produces a violation.
  - Out of range `threads` (< 1, > 1024) produces violations.
  - Out of range `hashMb` (< 1) produces a violation.
  - Out of range `startupTimeoutSeconds` (< 1) produces a violation.
  - Null `stockfish` configuration record produces a violation.

## Verification Plan
1. Compile and run test suite: `mvn test -Dtest=ChessPropertiesValidationTest`.
2. Verify all 7 validation scenarios pass successfully.
