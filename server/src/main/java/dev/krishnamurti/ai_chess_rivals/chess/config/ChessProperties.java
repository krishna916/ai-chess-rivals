package dev.krishnamurti.ai_chess_rivals.chess.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
@Validated
public record ChessProperties(@NotNull @Valid Stockfish stockfish) {

    /**
     * Stockfish engine settings.
     *
     * @param path                   Path to the Stockfish executable (absolute or relative to CWD).
     * @param threads                Number of search threads (UCI option "Threads"). Default: 1.
     * @param hashMb                 Hash table size in MB (UCI option "Hash"). Default: 16.
     * @param startupTimeoutSeconds  Reserved for future timeout enforcement. Not enforced in this version.
     */
    public record Stockfish(
            @NotBlank String path,
            @Min(1) @Max(1024) int threads,
            @Min(1) @Max(33554432) int hashMb,
            @Min(1) @Max(300) int startupTimeoutSeconds
    ) {}
}
