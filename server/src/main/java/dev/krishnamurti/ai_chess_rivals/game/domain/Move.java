package dev.krishnamurti.ai_chess_rivals.game.domain;

public record Move(
    int sequenceNumber,
    PlayerColor playedBy,
    MoveNotation notation,
    BoardPosition positionAfterMove) {

  public Move {
    if (sequenceNumber <= 0) {
      throw new IllegalArgumentException("sequenceNumber must be positive");
    }
    if (playedBy == null) {
      throw new NullPointerException("playedBy must not be null");
    }
    if (notation == null) {
      throw new NullPointerException("notation must not be null");
    }
    if (positionAfterMove == null) {
      throw new NullPointerException("positionAfterMove must not be null");
    }
  }
}
