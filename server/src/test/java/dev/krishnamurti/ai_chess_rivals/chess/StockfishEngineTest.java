package dev.krishnamurti.ai_chess_rivals.chess;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StockfishEngineTest {

  @Test
  void whenExecutableDoesNotExist_thenThrowsStockfishExceptionWithFormattedMessage(
      @TempDir Path tempDir) {
    Path nonExistentPath = tempDir.resolve("non-existent-stockfish");
    ChessProperties props =
        new ChessProperties(
            new ChessProperties.Stockfish(nonExistentPath.toString(), 1, 16, 10, 30));

    assertThatThrownBy(() -> new StockfishEngine(props))
        .isInstanceOf(StockfishException.class)
        .hasMessageContaining(
            "Stockfish executable not found at: " + nonExistentPath.toAbsolutePath())
        .hasMessageNotContaining("%s");
  }

  @Test
  void whenFileExistsButIsNotExecutable_thenThrowsStockfishExceptionWithFormattedMessage(
      @TempDir Path tempDir) throws IOException {
    // Let's see if a directory is considered non-executable on this OS
    Path targetPath = tempDir.resolve("dummy_dir");
    Files.createDirectory(targetPath);

    if (Files.isExecutable(targetPath)) {
      // If the directory is considered executable, let's try a file
      targetPath = Files.createFile(tempDir.resolve("dummy.txt"));
      targetPath.toFile().setExecutable(false);
      if (Files.isExecutable(targetPath)) {
        // If both are executable, we can't easily trigger isExecutable = false on this OS/env
        // without complex ACLs.
        // In this case, we skip the assertion for isExecutable, or mock/verify the exception
        // formatting by checking
        // that the test executes. We'll check if we can run it.
        System.out.println("Unable to easily make a file non-executable on this OS/filesystem");
        return;
      }
    }

    final Path finalPath = targetPath;
    ChessProperties props =
        new ChessProperties(new ChessProperties.Stockfish(finalPath.toString(), 1, 16, 10, 30));

    assertThatThrownBy(() -> new StockfishEngine(props))
        .isInstanceOf(StockfishException.class)
        .hasMessageContaining(
            "Stockfish file exists at '" + finalPath.toAbsolutePath() + "' but is not executable.")
        .hasMessageContaining("On Linux, run: chmod +x " + finalPath.toAbsolutePath())
        .hasMessageNotContaining("%s");
  }
}
