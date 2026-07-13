package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchConflictException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchCooldownException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchDailyLimitException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchEngineException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MatchController.class)
public class MatchControllerAdvice {

  private static final String ALREADY_RUNNING_CODE = "MATCH_ALREADY_RUNNING";
  private static final String ALREADY_RUNNING_MESSAGE = "A match is already active.";
  private static final String COOLDOWN_CODE = "MATCH_COOLDOWN_ACTIVE";
  private static final String COOLDOWN_MESSAGE = "A new match cannot be started yet.";
  private static final String DAILY_LIMIT_CODE = "MATCH_DAILY_LIMIT_REACHED";
  private static final String DAILY_LIMIT_MESSAGE =
      "The configured daily match limit has been reached.";

  @ExceptionHandler(MatchNotFoundException.class)
  public ProblemDetail handleNotFound(MatchNotFoundException ex) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    detail.setTitle("Match Not Found");
    return detail;
  }

  @ExceptionHandler(MatchConflictException.class)
  public ResponseEntity<ProblemDetail> handleConflict(MatchConflictException ex) {
    ProblemDetail detail =
        controlledProblem(
            HttpStatus.CONFLICT, "Match Conflict", ALREADY_RUNNING_CODE, ALREADY_RUNNING_MESSAGE);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
  }

  @ExceptionHandler(MatchCooldownException.class)
  public ResponseEntity<ProblemDetail> handleCooldown(MatchCooldownException ex) {
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
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    detail.setTitle("Match Engine Error");
    return detail;
  }
}
