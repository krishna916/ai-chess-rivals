package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationRequested;
import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationResult;
import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationService;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Handles chess evaluation requests without exposing the chess service to other modules. */
@Component
@RequiredArgsConstructor
@Slf4j
final class ChessEvaluationEventHandler {

  private final ChessEvaluationService chessEvaluationService;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  void evaluate(ChessEvaluationRequested request) {
    try {
      PositionEvaluation evaluation = chessEvaluationService.evaluate(request.fen());
      Optional<EvaluationSwing> swing = compare(request, evaluation);
      eventPublisher.publishEvent(
          new ChessEvaluationResult(
              request.correlationId(),
              request.ply(),
              request.fen(),
              Optional.of(evaluation),
              swing));
    } catch (RuntimeException exception) {
      log.warn(
          "Chess evaluation unavailable for ply {} and position {}",
          request.ply(),
          request.fen(),
          exception);
      eventPublisher.publishEvent(ChessEvaluationResult.unavailable(request));
    }
  }

  private Optional<EvaluationSwing> compare(
      ChessEvaluationRequested request, PositionEvaluation evaluation) {
    if (request.before().isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          chessEvaluationService.compare(request.before().orElseThrow(), evaluation));
    } catch (RuntimeException exception) {
      log.warn("Chess evaluation swing unavailable for ply {}", request.ply(), exception);
      return Optional.empty();
    }
  }
}
