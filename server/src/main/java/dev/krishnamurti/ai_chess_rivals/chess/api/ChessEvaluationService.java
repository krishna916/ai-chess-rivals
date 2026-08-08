package dev.krishnamurti.ai_chess_rivals.chess.api;

/**
 * Evaluates adjacent chess positions and compares them from the mover's perspective.
 *
 * <p>The before position is evaluated with the mover to move and the after position is evaluated
 * with the opponent to move. Implementations normalize both scores so positive swing means the
 * mover improved and negative swing means the mover worsened.
 */
public interface ChessEvaluationService {

  PositionEvaluation evaluate(String fen);

  EvaluationSwing compare(PositionEvaluation before, PositionEvaluation after);
}
