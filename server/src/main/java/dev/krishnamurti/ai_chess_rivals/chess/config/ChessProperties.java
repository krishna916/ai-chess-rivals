package dev.krishnamurti.ai_chess_rivals.chess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration properties for the chess module.
 *
 * <p>Bound from the {@code app.chess} prefix in {@code application.yaml}.
 * Override individual properties via environment variables.
 *
 * <pre>
 * app:
 *   chess:
 *     stockfish:
 *       path: ${STOCKFISH_PATH:stockfish/stockfish}
 *       threads: 1
 *       hash-mb: 16
 *       startup-timeout-seconds: 10
 * </pre>
 *
 * @param stockfish Stockfish engine settings.
 */
@ConfigurationProperties(prefix = "app.chess")
public record ChessProperties(Stockfish stockfish) {

    /**
     * Stockfish engine settings.
     *
     * @param path                   Path to the Stockfish executable (absolute or relative to CWD).
     * @param threads                Number of search threads (UCI option "Threads"). Default: 1.
     * @param hashMb                 Hash table size in MB (UCI option "Hash"). Default: 16.
     * @param startupTimeoutSeconds  Reserved for future timeout enforcement. Not enforced in this version.
     */
    public record Stockfish(
            String path,
            int threads,
            int hashMb,
            int startupTimeoutSeconds
    ) {}
}
