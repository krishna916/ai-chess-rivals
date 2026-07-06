package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proves that Stockfish starts, identifies itself correctly, and responds
 * to the UCI readiness check. Standalone — no Spring context, no database.
 *
 * Run with: mvn test -Dtest=StockfishClientValidationTest -DSTOCKFISH_PATH=stockfish/stockfish.exe
 */
class StockfishClientValidationTest {

    private static final Logger log = LoggerFactory.getLogger(StockfishClientValidationTest.class);

    @Test
    void stockfishStartsAndPassesReadinessCheck() {
        String path = resolveStockfishPath();
        log.info("=== StockfishClientValidationTest: using executable at '{}' ===", path);

        ChessProperties props = new ChessProperties(new ChessProperties.Stockfish(path, 1, 16, 10, 30));
        StockfishClient client = new StockfishEngine(props);
        try {
            // Validation happened in constructor: uci → uciok → setoptions → isready → readyok
            log.info("=== Stockfish startup validation PASSED ===");
        } finally {
            client.close();
        }
    }

    private static String resolveStockfishPath() {
        return StockfishTestHelper.resolveStockfishPath();
    }
}
