package dev.krishnamurti.ai_chess_rivals.chess;

import java.util.Optional;

/**
 * Wraps a single line of output received from the Stockfish engine over UCI.
 *
 * <p>Package-private. Use the helper methods to extract structured data
 * instead of parsing raw strings at call sites.
 */
record UciResponse(String raw) {

    /** Returns {@code true} if the response line contains the given token. */
    boolean contains(String token) {
        return raw.contains(token);
    }

    /** Returns {@code true} if the response line starts with the given prefix. */
    boolean startsWith(String prefix) {
        return raw.startsWith(prefix);
    }

    /**
     * Extracts the value that follows a known prefix on this response line.
     * Example: {@code "id name Stockfish 17"} with prefix {@code "id name "} → {@code "Stockfish 17"}.
     *
     * @param prefix the expected line prefix
     * @return the trimmed value after the prefix, or empty if this line does not start with it
     */
    Optional<String> extractField(String prefix) {
        if (!raw.startsWith(prefix)) {
            return Optional.empty();
        }
        String value = raw.substring(prefix.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    /**
     * Extracts the best move token from a {@code "bestmove <move> ..."} response.
     *
     * <p>Returns {@link Optional#empty()} for the Stockfish sentinel {@code "(none)"},
     * which is emitted when no legal move exists (e.g. checkmate or stalemate).
     *
     * @return the move in UCI notation (e.g. {@code "e2e4"}), or empty if this is not a
     *         bestmove line or the engine has no legal move to report
     */
    Optional<String> extractBestMove() {
        if (!raw.startsWith("bestmove")) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\s+");
        if (parts.length < 2 || "(none)".equals(parts[1])) {
            return Optional.empty();
        }
        return Optional.of(parts[1]);
    }
}
