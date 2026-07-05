package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.fail;

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

        ChessProperties props = new ChessProperties(new ChessProperties.Stockfish(path, 1, 16, 10));
        StockfishClient client = new StockfishEngine(props);
        try {
            // Validation happened in constructor: uci → uciok → setoptions → isready → readyok
            log.info("=== Stockfish startup validation PASSED ===");
        } finally {
            client.close();
        }
    }

    private static String resolveStockfishPath() {
        String path = System.getenv("STOCKFISH_PATH");
        if (path == null || path.isBlank()) {
            path = "stockfish/stockfish.exe";
        }
        if (!Files.exists(Paths.get(path))) {
            fail("Stockfish binary not found at '" + path + "'. "
                    + "Run: mvn generate-resources -Pwindows  (or -Plinux)");
        }
        return path;
    }
}
