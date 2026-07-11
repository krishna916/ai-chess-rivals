package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.List;

public record MatchResponse(
    PlayerColor sideToMove,
    String fen,
    List<MoveResponse> moves,
    GameStatus status,
    GameResult result,
    boolean running) {

  public MatchResponse(
      PlayerColor sideToMove,
      String fen,
      List<MoveResponse> moves,
      GameStatus status,
      GameResult result,
      boolean running) {
    this.sideToMove = sideToMove;
    this.fen = fen;
    this.moves = moves != null ? List.copyOf(moves) : List.of();
    this.status = status;
    this.result = result;
    this.running = running;
  }
}
