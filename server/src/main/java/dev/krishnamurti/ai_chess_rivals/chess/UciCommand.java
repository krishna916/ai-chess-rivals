package dev.krishnamurti.ai_chess_rivals.chess;

/**
 * Represents a single UCI command to be sent to the Stockfish engine.
 *
 * <p>Package-private. Use the static factory methods to construct commands;
 * never build raw UCI strings outside this type.
 */
record UciCommand(String text) {

    /** Initiates the UCI handshake. Engine responds with id lines then {@code uciok}. */
    static UciCommand uci() {
        return new UciCommand("uci");
    }

    /** Asks the engine whether it is ready to receive commands. Engine responds {@code readyok}. */
    static UciCommand isReady() {
        return new UciCommand("isready");
    }

    /** Signals the start of a new game, resetting internal state. */
    static UciCommand newGame() {
        return new UciCommand("ucinewgame");
    }

    /** Asks the engine to quit cleanly. */
    static UciCommand quit() {
        return new UciCommand("quit");
    }

    /**
     * Sets the board position.
     *
     * @param fen FEN string, or {@code null} / blank / {@code "startpos"} for the starting position
     */
    static UciCommand position(String fen) {
        if (fen == null || fen.isBlank() || "startpos".equals(fen)) {
            return new UciCommand("position startpos");
        }
        return new UciCommand("position fen " + fen);
    }

    /**
     * Instructs the engine to search for the best move within a fixed time budget.
     *
     * @param moveTimeMs maximum thinking time in milliseconds
     */
    static UciCommand go(long moveTimeMs) {
        return new UciCommand("go movetime " + moveTimeMs);
    }

    /**
     * Sets a UCI engine option.
     *
     * @param name  option name (e.g. {@code "Threads"}, {@code "Hash"})
     * @param value option value
     */
    static UciCommand setOption(String name, Object value) {
        return new UciCommand("setoption name " + name + " value " + value);
    }

    @Override
    public String toString() {
        return text;
    }
}
