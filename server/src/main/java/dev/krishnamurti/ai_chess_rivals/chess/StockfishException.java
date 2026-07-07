package dev.krishnamurti.ai_chess_rivals.chess;

/**
 * Thrown when the Stockfish engine cannot be started, becomes unresponsive, or fails to return an
 * expected UCI response.
 *
 * <p>This is a {@link RuntimeException} so callers are not forced to declare checked exceptions —
 * the engine becoming unavailable is typically fatal for the application context.
 */
public class StockfishException extends RuntimeException {

  public StockfishException(String message) {
    super(message);
  }

  public StockfishException(String message, Throwable cause) {
    super(message, cause);
  }
}
