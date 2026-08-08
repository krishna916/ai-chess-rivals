package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import org.junit.jupiter.api.Test;

class UciResponseTest {

  @Test
  void extractsCentipawnScoreFromInfoLine() {
    PositionEvaluation score =
        new UciResponse("info depth 8 seldepth 10 score cp 37 nodes 1234 pv e2e4")
            .extractScore()
            .orElseThrow();

    assertThat(score.type()).isEqualTo(PositionEvaluation.ScoreType.CENTIPAWNS);
    assertThat(score.value()).isEqualTo(37);
    assertThat(score.comparableCentipawns()).isEqualTo(37L);
  }

  @Test
  void extractsNegativeCentipawnScore() {
    PositionEvaluation score =
        new UciResponse("info depth 8 score cp -215 nodes 99").extractScore().orElseThrow();

    assertThat(score.value()).isEqualTo(-215);
    assertThat(score.comparableCentipawns()).isEqualTo(-215L);
  }

  @Test
  void extractsPositiveMateScoreAsBoundedWinningValue() {
    PositionEvaluation score =
        new UciResponse("info depth 8 score mate 3 nodes 99").extractScore().orElseThrow();

    assertThat(score.type()).isEqualTo(PositionEvaluation.ScoreType.MATE);
    assertThat(score.value()).isEqualTo(3);
    assertThat(score.comparableCentipawns()).isEqualTo(100_000L);
  }

  @Test
  void extractsNegativeMateScoreAsBoundedLosingValue() {
    PositionEvaluation score =
        new UciResponse("info depth 8 score mate -2 nodes 99").extractScore().orElseThrow();

    assertThat(score.comparableCentipawns()).isEqualTo(-100_000L);
  }

  @Test
  void treatsMateZeroAsAlreadyCheckmatedForSideToMove() {
    PositionEvaluation score =
        new UciResponse("info depth 0 score mate 0").extractScore().orElseThrow();

    assertThat(score.comparableCentipawns()).isEqualTo(-100_000L);
  }

  @Test
  void returnsEmptyWhenLineDoesNotContainAScore() {
    assertThat(new UciResponse("info depth 8 nodes 99 pv e2e4").extractScore()).isEmpty();
    assertThat(new UciResponse("bestmove e2e4").extractScore()).isEmpty();
  }

  @Test
  void retainsTheLatestParsableInfoScore() {
    PositionEvaluation first =
        StockfishEngine.latestScore(null, new UciResponse("info depth 4 score cp 12 nodes 10"));
    PositionEvaluation latest =
        StockfishEngine.latestScore(first, new UciResponse("info depth 8 score cp -34 nodes 20"));

    assertThat(latest.value()).isEqualTo(-34);
  }
}
