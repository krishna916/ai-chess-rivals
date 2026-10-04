package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.InvalidPersonalitySelectionException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchConflictException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchCooldownException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchDailyLimitException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchEngineException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MatchController.class)
@Slf4j
public class MatchControllerAdvice {

  @ExceptionHandler(InvalidPersonalitySelectionException.class)
  public ProblemDetail handleInvalidPersonalitySelection(
      InvalidPersonalitySelectionException exception) {
    log.info(
        "event=controller.rejected operation=match_start outcome=invalid_personality_selection");
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    detail.setTitle("Invalid Personality Selection");
    detail.setProperty("code", "INVALID_PERSONALITY_SELECTION");
    detail.setProperty("message", exception.getMessage());
    return detail;
  }

  private static final String ALREADY_RUNNING_CODE = "MATCH_ALREADY_RUNNING";
  private static final String ALREADY_RUNNING_MESSAGE = "A match is already active.";
  private static final String COOLDOWN_CODE = "MATCH_COOLDOWN_ACTIVE";
  private static final String COOLDOWN_MESSAGE = "A new match cannot be started yet.";
  private static final String DAILY_LIMIT_CODE = "MATCH_DAILY_LIMIT_REACHED";
  private static final String DAILY_LIMIT_MESSAGE =
      "The configured daily match limit has been reached.";

  @ExceptionHandler(MatchNotFoundException.class)
  public ProblemDetail handleNotFound(MatchNotFoundException ex) {
    log.info("event=controller.rejected operation=match_current outcome=match_not_found");
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    detail.setTitle("Match Not Found");
    return detail;
  }

  @ExceptionHandler(MatchConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflict(MatchConflictException ex) {
    log.info("event=controller.rejected operation=match_start outcome={}", ALREADY_RUNNING_CODE);
    ProblemDetail detail =
        controlledProblem(
            HttpStatus.CONFLICT, "Match Conflict", ALREADY_RUNNING_CODE, ALREADY_RUNNING_MESSAGE);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
  }

  @ExceptionHandler(MatchCooldownException.class)
  public ResponseEntity<ProblemDetail> handleCooldown(MatchCooldownException ex) {
    log.info("event=controller.rejected operation=match_start outcome={}", COOLDOWN_CODE);
    ProblemDetail detail =
        controlledProblem(
            HttpStatus.TOO_MANY_REQUESTS, "Match Start Limited", COOLDOWN_CODE, COOLDOWN_MESSAGE);
    detail.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
        .body(detail);
  }

  @ExceptionHandler(MatchDailyLimitException.class)
  public ResponseEntity<ProblemDetail> handleDailyLimit(MatchDailyLimitException ex) {
    log.info("event=controller.rejected operation=match_start outcome={}", DAILY_LIMIT_CODE);
    ProblemDetail detail =
        controlledProblem(
            HttpStatus.TOO_MANY_REQUESTS,
            "Match Start Limited",
            DAILY_LIMIT_CODE,
            DAILY_LIMIT_MESSAGE);
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(detail);
  }

  private static ProblemDetail controlledProblem(
      HttpStatus status, String title, String code, String message) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(title);
    detail.setProperty("code", code);
    detail.setProperty("message", message);
    return detail;
  }

  @ExceptionHandler(MatchEngineException.class)
  public ProblemDetail handleEngineException(MatchEngineException ex) {
    log.info("event=controller.failed operation=match_control outcome=engine_error");
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    detail.setTitle("Match Engine Error");
    return detail;
  }
}
