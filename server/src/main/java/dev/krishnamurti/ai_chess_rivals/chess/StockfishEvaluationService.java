package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationService;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.time.Duration;
import java.util.Objects;

final class StockfishEvaluationService implements ChessEvaluationService {

  private final StockfishClient client;
  private final int depth;
  private final Duration moveTime;
  private final int gainThreshold;
  private final int mistakeThreshold;

  StockfishEvaluationService(StockfishClient client, ChessProperties properties) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    ChessProperties.Stockfish.Evaluation config =
        Objects.requireNonNull(properties, "properties must not be null").stockfish().evaluation();
    this.depth = config.depth();
    this.moveTime = Duration.ofMillis(config.moveTimeMillis());
    this.gainThreshold = config.majorGainThresholdCentipawns();
    this.mistakeThreshold = config.majorMistakeThresholdCentipawns();
  }

  @Override
  public PositionEvaluation evaluate(String fen) {
    if (fen == null || fen.isBlank()) {
      throw new IllegalArgumentException("fen must not be blank");
    }
    client.setPosition(fen);
    return client.evaluate(depth, moveTime);
  }

  @Override
  public EvaluationSwing compare(PositionEvaluation before, PositionEvaluation after) {
    Objects.requireNonNull(before, "before must not be null");
    Objects.requireNonNull(after, "after must not be null");

    long normalizedBefore = before.comparableCentipawns();
    long normalizedAfter = -after.comparableCentipawns();
    long swing = normalizedAfter - normalizedBefore;

    EvaluationSwingClassification classification;
    if (swing >= gainThreshold) {
      classification = EvaluationSwingClassification.MAJOR_GAIN;
    } else if (swing <= -mistakeThreshold) {
      classification = EvaluationSwingClassification.MAJOR_MISTAKE;
    } else {
      classification = EvaluationSwingClassification.STABLE;
    }

    return new EvaluationSwing(normalizedBefore, normalizedAfter, swing, classification);
  }
}
