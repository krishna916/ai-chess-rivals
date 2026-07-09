package dev.krishnamurti.ai_chess_rivals.game.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Match {

  private final PlayerColor sideToMove;
  private final BoardPosition currentPosition;
  private final List<Move> moves;
  private final GameStatus status;
  private final GameResult result;

  public Match(
      PlayerColor sideToMove,
      BoardPosition currentPosition,
      List<Move> moves,
      GameStatus status,
      GameResult result) {
    this.sideToMove = Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    this.currentPosition =
        Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    this.moves = List.copyOf(Objects.requireNonNull(moves, "moves must not be null"));
    this.status = Objects.requireNonNull(status, "status must not be null");
    if (status != GameStatus.FINISHED && result != null) {
      throw new IllegalArgumentException("result is only allowed when status is FINISHED");
    }
    this.result = result;
  }

  public static Match newGame() {
    return new Match(
        PlayerColor.WHITE,
        BoardPosition.STARTING_POSITION,
        List.of(),
        GameStatus.IN_PROGRESS,
        null);
  }

  public PlayerColor sideToMove() {
    return sideToMove;
  }

  public BoardPosition currentPosition() {
    return currentPosition;
  }

  public GameStatus status() {
    return status;
  }

  public boolean isInProgress() {
    return status == GameStatus.IN_PROGRESS;
  }

  public boolean isFinished() {
    return status == GameStatus.FINISHED;
  }

  public int moveCount() {
    return moves.size();
  }

  public List<Move> moves() {
    return moves;
  }

  public Optional<GameResult> result() {
    return Optional.ofNullable(result);
  }
}
