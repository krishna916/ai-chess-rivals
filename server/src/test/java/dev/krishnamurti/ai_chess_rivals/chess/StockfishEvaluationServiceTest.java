package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationService;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StockfishEvaluationServiceTest {

  private final FakeStockfishClient client = new FakeStockfishClient();
  private ChessEvaluationService service;

  @BeforeEach
  void setUp() {
    service = new StockfishEvaluationService(client, properties());
  }

  @Test
  void compareNormalizesPostMoveScoreToMoverPerspective() {
    EvaluationSwing swing = service.compare(cp(20), cp(-260));

    assertThat(swing.beforeCentipawns()).isEqualTo(20);
    assertThat(swing.afterCentipawns()).isEqualTo(260);
    assertThat(swing.swingCentipawns()).isEqualTo(240);
    assertThat(swing.classification()).isEqualTo(EvaluationSwingClassification.MAJOR_GAIN);
  }

  @Test
  void gainThresholdIsInclusive() {
    assertThat(service.compare(cp(0), cp(-200)).classification())
        .isEqualTo(EvaluationSwingClassification.MAJOR_GAIN);
  }

  @Test
  void mistakeThresholdIsInclusive() {
    assertThat(service.compare(cp(0), cp(200)).classification())
        .isEqualTo(EvaluationSwingClassification.MAJOR_MISTAKE);
  }

  @Test
  void oneCentipawnInsideThresholdRemainsStable() {
    assertThat(service.compare(cp(0), cp(-199)).classification())
        .isEqualTo(EvaluationSwingClassification.STABLE);
    assertThat(service.compare(cp(0), cp(199)).classification())
        .isEqualTo(EvaluationSwingClassification.STABLE);
  }

  @Test
  void mateScoresDoNotOverflowOrInvertClassification() {
    EvaluationSwing swing = service.compare(cp(0), mate(0));

    assertThat(swing.afterCentipawns()).isEqualTo(100_000L);
    assertThat(swing.swingCentipawns()).isEqualTo(100_000L);
    assertThat(swing.classification()).isEqualTo(EvaluationSwingClassification.MAJOR_GAIN);
  }

  @Test
  void evaluateSetsFenAndUsesConfiguredBounds() {
    PositionEvaluation expected = cp(-37);
    client.evaluations.add(expected);

    assertThat(service.evaluate("position-fen")).isSameAs(expected);
    assertThat(client.fens).containsExactly("position-fen");
    assertThat(client.depths).containsExactly(8);
    assertThat(client.moveTimes).containsExactly(Duration.ofMillis(50));
  }

  private static ChessProperties properties() {
    return new ChessProperties(
        new ChessProperties.Stockfish(
            "stockfish/stockfish.exe",
            1,
            16,
            10,
            30,
            new ChessProperties.Stockfish.Evaluation(8, 50, 200, 200)));
  }

  private static PositionEvaluation cp(int value) {
    return new PositionEvaluation(PositionEvaluation.ScoreType.CENTIPAWNS, value);
  }

  private static PositionEvaluation mate(int value) {
    return new PositionEvaluation(PositionEvaluation.ScoreType.MATE, value);
  }

  private static final class FakeStockfishClient implements StockfishClient {

    private final Deque<PositionEvaluation> evaluations = new ArrayDeque<>();
    private final List<String> fens = new ArrayList<>();
    private final List<Integer> depths = new ArrayList<>();
    private final List<Duration> moveTimes = new ArrayList<>();

    @Override
    public void newGame() {}

    @Override
    public void setPosition(String fen) {
      fens.add(fen);
    }

    @Override
    public String bestMove(Duration thinkTime) {
      return "e2e4";
    }

    @Override
    public PositionEvaluation evaluate(int depth, Duration moveTime) {
      depths.add(depth);
      moveTimes.add(moveTime);
      return evaluations.removeFirst();
    }

    @Override
    public void close() {}
  }
}
