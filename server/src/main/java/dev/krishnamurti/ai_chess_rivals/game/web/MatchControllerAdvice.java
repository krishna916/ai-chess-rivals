package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchConflictException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchEngineException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MatchController.class)
public class MatchControllerAdvice {

  @ExceptionHandler(MatchNotFoundException.class)
  public ProblemDetail handleNotFound(MatchNotFoundException ex) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    detail.setTitle("Match Not Found");
    return detail;
  }

  @ExceptionHandler(MatchConflictException.class)
  public ProblemDetail handleConflict(MatchConflictException ex) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    detail.setTitle("Match Conflict");
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
