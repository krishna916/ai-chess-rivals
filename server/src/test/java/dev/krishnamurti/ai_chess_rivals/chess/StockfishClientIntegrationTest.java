package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link StockfishClient}. Requires a real Stockfish binary — set
 * STOCKFISH_PATH env var or use the default.
 *
 * <p>Run with: mvn test -Dtest=StockfishClientIntegrationTest
 * -DSTOCKFISH_PATH=stockfish/stockfish.exe
 */
class StockfishClientIntegrationTest {

  private StockfishClient client;

  @BeforeEach
  void startEngine() {
    String path = StockfishTestHelper.resolveStockfishPath();
    ChessProperties props = new ChessProperties(new ChessProperties.Stockfish(path, 1, 16, 10, 30));
    client = new StockfishEngine(props);
  }

  @AfterEach
  void stopEngine() {
    if (client != null) {
      client.close();
    }
  }

  /**
   * AC1 + AC2: Engine starts and UCI handshake completes. Verified implicitly by @BeforeEach
   * reaching this point without exception.
   */
  @Test
  void engineStartsAndUciHandshakeCompletes() {
    assertThat(client).isNotNull();
  }

  /** AC3: Backend can request a best move from the starting position. */
  @Test
  void bestMoveReturnsLegalMoveFromStartingPosition() {
    client.newGame();
    client.setPosition("startpos");

    String move = client.bestMove(Duration.ofMillis(500));

    // UCI move format: source-square + dest-square + optional promotion piece
    assertThat(move).isNotNull().isNotBlank().matches("[a-h][1-8][a-h][1-8][qrbn]?");
  }

  /** AC3 (variant): Best move from a specific FEN position. */
  @Test
  void bestMoveReturnsLegalMoveFromCustomPosition() {
    client.newGame();
    // Position after 1.f3 e5 2.g4 — black has the Fool's mate in one
    client.setPosition("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2");

    String move = client.bestMove(Duration.ofMillis(500));

    assertThat(move).isNotNull().isNotBlank().matches("[a-h][1-8][a-h][1-8][qrbn]?");
  }

  /** AC3 (idempotency): newGame() and full move cycle can be repeated. */
  @Test
  void newGameCanBeCalledMultipleTimes() {
    client.newGame();
    client.setPosition("startpos");
    client.bestMove(Duration.ofMillis(200));

    client.newGame();
    client.setPosition("startpos");
    String move = client.bestMove(Duration.ofMillis(200));

    assertThat(move).isNotBlank();
  }

  /** AC4: Engine process shuts down cleanly; close() is idempotent. */
  @Test
  void closeIsIdempotent() {
    client.close();
    client.close(); // must not throw
  }
}
