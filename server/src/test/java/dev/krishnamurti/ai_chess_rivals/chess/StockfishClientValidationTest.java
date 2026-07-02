package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves that Stockfish starts, identifies itself correctly, and responds
 * to the UCI readiness check.  This test is intentionally standalone — it
 * does not load the Spring context and therefore needs no database.
 *
 * Run with: mvn test -Dtest=StockfishClientValidationTest -DSTOCKFISH_PATH=stockfish/stockfish.exe
 */
class StockfishClientValidationTest {

    private static final Logger log = LoggerFactory.getLogger(StockfishClientValidationTest.class);

    @Test
    void stockfishStartsAndPassesReadinessCheck() throws IOException {
        String path = System.getenv("STOCKFISH_PATH");
        if (path == null || path.isBlank()) {
            path = "stockfish/stockfish.exe";   // Windows default
        }

        if (!Files.exists(Paths.get(path))) {
            fail("Stockfish binary not found at '" + path + "'. "
                    + "Run: mvn generate-resources -Pwindows  (or -Plinux)");
        }

        log.info("=== StockfishClientValidationTest: using executable at '{}' ===", path);

        ChessProperties props = new ChessProperties(new ChessProperties.Stockfish(path));

        try (var client = new AutoCloseableStockfishClient(props)) {
            // Validation happened inside the constructor (uci → uciok → isready → readyok).
            // If we get here without an exception, the engine is working.
            log.info("=== Stockfish validation test PASSED ===");
        }
    }

    /** Thin wrapper so try-with-resources calls shutdown(). */
    static class AutoCloseableStockfishClient implements AutoCloseable {
        private final StockfishClient client;

        AutoCloseableStockfishClient(ChessProperties props) throws IOException {
            this.client = new StockfishClient(props);
        }

        @Override
        public void close() {
            client.shutdown();
        }
    }
}
