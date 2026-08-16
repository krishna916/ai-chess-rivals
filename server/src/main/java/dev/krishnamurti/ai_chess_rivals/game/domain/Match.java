package dev.krishnamurti.ai_chess_rivals.game.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Match {

  private final UUID id;
  private final PlayerColor sideToMove;
  private final BoardPosition currentPosition;
  private final List<Move> moves;
  private final GameStatus status;
  private final GameResult result;
  private final MatchRivalry rivalry;

  public Match(
      UUID id,
      PlayerColor sideToMove,
      BoardPosition currentPosition,
      List<Move> moves,
      GameStatus status,
      GameResult result,
      MatchRivalry rivalry) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.sideToMove = Objects.requireNonNull(sideToMove, "sideToMove must not be null");
    this.currentPosition =
        Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    this.moves = List.copyOf(Objects.requireNonNull(moves, "moves must not be null"));
    this.status = Objects.requireNonNull(status, "status must not be null");
    if (status == GameStatus.FINISHED && result == null) {
      throw new IllegalArgumentException("result is required when status is FINISHED");
    }
    if (status != GameStatus.FINISHED && result != null) {
      throw new IllegalArgumentException("result is only allowed when status is FINISHED");
    }
    this.result = result;
    this.rivalry = Objects.requireNonNull(rivalry, "rivalry must not be null");
  }

  public Match(
      PlayerColor sideToMove,
      BoardPosition currentPosition,
      List<Move> moves,
      GameStatus status,
      GameResult result,
      MatchRivalry rivalry) {
    this(UUID.randomUUID(), sideToMove, currentPosition, moves, status, result, rivalry);
  }

  public static Match newGame(MatchRivalry rivalry) {
    return new Match(
        UUID.randomUUID(),
        PlayerColor.WHITE,
        BoardPosition.STARTING_POSITION,
        List.of(),
        GameStatus.IN_PROGRESS,
        null,
        rivalry);
  }

  public UUID id() {
    return id;
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

  public MatchRivalry rivalry() {
    return rivalry;
  }

  public Match recordMove(
      MoveNotation notation, BoardPosition positionAfterMove, MoveDetails details) {
    requireInProgress("record a move");

    Move move = new Move(moveCount() + 1, sideToMove, notation, positionAfterMove, details);
    List<Move> updatedMoves = new java.util.ArrayList<>(moves);
    updatedMoves.add(move);

    return new Match(
        id,
        sideToMove.opposite(),
        positionAfterMove,
        updatedMoves,
        GameStatus.IN_PROGRESS,
        null,
        rivalry);
  }

  public Match finish(GameResult result) {
    requireInProgress("finish a match");
    return new Match(id, sideToMove, currentPosition, moves, GameStatus.FINISHED, result, rivalry);
  }

  private void requireInProgress(String action) {
    if (!isInProgress()) {
      throw new IllegalStateException("Cannot " + action + " when match is not in progress");
    }
  }
}
