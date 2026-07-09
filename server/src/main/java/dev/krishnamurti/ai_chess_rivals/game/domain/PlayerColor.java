package dev.krishnamurti.ai_chess_rivals.game.domain;

public enum PlayerColor {
  WHITE,
  BLACK;

  public PlayerColor opposite() {
    return switch (this) {
      case WHITE -> BLACK;
      case BLACK -> WHITE;
    };
  }
}
