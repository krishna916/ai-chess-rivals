package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Low-level client that communicates with the Stockfish chess engine
 * over its UCI (Universal Chess Interface) protocol via stdin/stdout.
 *
 * <p>The executable path comes exclusively from {@link ChessProperties}.
 * No OS detection is performed here — the application simply executes
 * whatever path is configured. Set the {@code STOCKFISH_PATH} environment
 * variable (or {@code app.chess.stockfish.path} in application.yaml) to
 * point to the correct binary for the current platform.
 *
 * <p>On startup the client:
 * <ol>
 *     <li>Resolves the configured path.</li>
 *     <li>Verifies the file exists and is executable, producing a clear
 *         error message if it does not.</li>
 *     <li>Launches the process with {@link ProcessBuilder}.</li>
 * </ol>
 */
@Slf4j
class StockfishEngine implements StockfishClient {

    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    StockfishEngine(ChessProperties chessProperties) throws IOException {
        String configuredPath = chessProperties.stockfish().path();
        Path executablePath = Paths.get(configuredPath);

        log.info("Stockfish executable configured at: {}", executablePath.toAbsolutePath());

        if (!Files.exists(executablePath)) {
            throw new IllegalStateException(
                    """
                    Stockfish executable not found at: %s
                    
                    To fix this, download the correct binary for your platform:
                    
                      Windows:  mvn package -Pwindows    (from the server/ directory)
                      Linux:    mvn package -Plinux      (from the server/ directory)
                    
                    Then set STOCKFISH_PATH if using a non-default location:
                    
                      Windows:  STOCKFISH_PATH=stockfish/stockfish.exe
                      Linux:    STOCKFISH_PATH=stockfish/stockfish
                    
                    See server/README.md for full instructions.
                    """.formatted(executablePath.toAbsolutePath())
            );
        }

        if (!Files.isExecutable(executablePath)) {
            throw new IllegalStateException(
                    "Stockfish file exists at '%s' but is not executable. "
                    + "On Linux, run: chmod +x %s"
                            .formatted(executablePath.toAbsolutePath(), executablePath.toAbsolutePath())
            );
        }

        log.info("Launching Stockfish: {}", executablePath.toAbsolutePath());
        this.process = new ProcessBuilder(executablePath.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();

        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        sendCommand("uci");
        String engineName = "Unknown";
        String engineAuthor = "Unknown";
        String line;
        boolean uciokReceived = false;

        while ((line = reader.readLine()) != null) {
            log.debug("<<< {}", line);
            if (line.startsWith("id name ")) {
                engineName = line.substring(8).trim();
            } else if (line.startsWith("id author ")) {
                engineAuthor = line.substring(10).trim();
            } else if (line.contains("uciok")) {
                uciokReceived = true;
                break;
            }
        }

        if (!uciokReceived) {
            throw new IllegalStateException("Stockfish process ended before responding with 'uciok'");
        }

        // Perform a readiness check to guarantee correct operation
        sendCommand("isready");
        waitForResponse("readyok");

        log.info("=== Stockfish Startup Validation Successful ===");
        log.info("Engine Name:   {}", engineName);
        log.info("Engine Author: {}", engineAuthor);
        log.info("Status:        READY (received readyok)");
        log.info("===============================================");
    }

    /**
     * Sends a raw UCI command to the Stockfish process.
     *
     * @param command the UCI command string (e.g. {@code "isready"}, {@code "quit"})
     * @throws IOException if writing to the process stream fails
     */
    public void sendCommand(String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
        log.debug(">>> {}", command);
    }

    /**
     * Reads lines from Stockfish until a line containing the expected token is found.
     *
     * @param expected substring to look for in Stockfish's output
     * @return the matching line
     * @throws IOException if reading fails
     * @throws IllegalStateException if the process terminates before the expected token arrives
     */
    public String waitForResponse(String expected) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            log.debug("<<< {}", line);
            if (line.contains(expected)) {
                return line;
            }
        }
        throw new IllegalStateException(
                "Stockfish process ended before responding with expected token: " + expected);
    }

    /**
     * Gracefully shuts down the Stockfish process when the Spring context closes.
     */
    @PreDestroy
    @Override
    public void close() {
        try {
            sendCommand("quit");
        } catch (IOException e) {
            log.warn("Could not send quit command to Stockfish: {}", e.getMessage());
        }
        process.destroy();
        log.info("Stockfish process terminated.");
    }

    // Legacy — kept until Task 5 rewrites this class
    public void newGame() {}
    public void setPosition(String fen) {}
    public java.time.Duration thinkTime = null;
    public String bestMove(java.time.Duration d) { return ""; }
}
