package dev.krishnamurti.ai_chess_rivals.chess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration properties for the chess module.
 *
 * <p>Bound from the {@code app.chess} prefix in {@code application.yaml}.
 * Override {@code STOCKFISH_PATH} environment variable to change the executable
 * location without modifying configuration files.
 *
 * <pre>
 * app:
 *   chess:
 *     stockfish:
 *       path: ${STOCKFISH_PATH:stockfish/stockfish}
 * </pre>
 *
 * @param stockfish Stockfish engine settings.
 */
@ConfigurationProperties(prefix = "app.chess")
public record ChessProperties(Stockfish stockfish) {

    /**
     * Stockfish engine settings.
     *
     * @param path Path to the Stockfish executable. May be absolute or relative
     *             to the working directory from which the application is launched.
     *             No OS detection is performed here; the application simply
     *             executes whatever path is configured.
     */
    public record Stockfish(String path) {}
}
