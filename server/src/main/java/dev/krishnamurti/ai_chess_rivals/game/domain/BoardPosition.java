package dev.krishnamurti.ai_chess_rivals.game.domain;

public record BoardPosition(String fen) {

  public static final BoardPosition STARTING_POSITION =
      new BoardPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

  public BoardPosition {
    if (fen == null) {
      throw new NullPointerException("fen must not be null");
    }
    if (fen.isBlank()) {
      throw new IllegalArgumentException("fen must not be blank");
    }
  }
}
