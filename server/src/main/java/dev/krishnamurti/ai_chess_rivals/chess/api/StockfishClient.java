package dev.krishnamurti.ai_chess_rivals.chess.api;

import dev.krishnamurti.ai_chess_rivals.chess.StockfishException;
import java.time.Duration;

/**
 * Encapsulates all communication with the Stockfish chess engine.
 *
 * <p>Implementations launch and manage the engine process, perform the UCI handshake, and expose a
 * small, stable API to the rest of the application. Raw UCI commands and protocol details must not
 * be exposed beyond this interface.
 */
public interface StockfishClient {

  /**
   * Resets the engine to a clean state for a new game. Sends {@code ucinewgame} followed by an
   * {@code isready} sync.
   *
   * @throws StockfishException if the engine does not respond
   */
  void newGame();

  /**
   * Sets the board position the engine will analyse from.
   *
   * @param fen FEN string describing the position, or {@code "startpos"} for the initial position
   * @throws StockfishException if the engine does not accept the position command
   */
  void setPosition(String fen);

  /**
   * Asks the engine to calculate the best move for the current position.
   *
   * @param thinkTime maximum time the engine may think
   * @return best move in UCI notation (e.g. {@code "e2e4"})
   * @throws StockfishException if the engine does not return a {@code bestmove} response
   */
  String bestMove(Duration thinkTime);

  /**
   * Gracefully shuts down the engine process. Safe to call multiple times; subsequent calls are
   * no-ops.
   */
  void close();
}
