package dev.krishnamurti.ai_chess_rivals.chess;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Shared test utility for the {@code chess} package tests.
 *
 * <p>Package-private — not part of the production API.
 */
final class StockfishTestHelper {

  private StockfishTestHelper() {}

  /**
   * Resolves the Stockfish binary path from the {@code STOCKFISH_PATH} environment variable,
   * falling back to {@code stockfish/stockfish.exe} when the variable is absent or blank.
   *
   * <p>Fails the calling test immediately via {@link org.junit.jupiter.api.Assertions#fail} if the
   * resolved path does not exist on the filesystem.
   *
   * @return absolute or relative path to a Stockfish executable that exists on disk
   */
  static String resolveStockfishPath() {
    String path = System.getenv("STOCKFISH_PATH");
    if (path == null || path.isBlank()) {
      path = "stockfish/stockfish.exe";
    }
    if (!Files.exists(Paths.get(path))) {
      fail(
          "Stockfish binary not found at '"
              + path
              + "'. "
              + "Run: mvn generate-resources -Pwindows  (or -Plinux)");
    }
    return path;
  }
}
