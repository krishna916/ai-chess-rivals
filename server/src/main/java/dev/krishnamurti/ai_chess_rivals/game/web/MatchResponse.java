package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.List;
import java.util.UUID;

public record MatchResponse(
    UUID matchId,
    MatchPersonalityResponse whitePersonality,
    MatchPersonalityResponse blackPersonality,
    PlayerColor sideToMove,
    String fen,
    List<MoveResponse> moves,
    GameStatus status,
    GameResult result,
    boolean running,
    MatchStartAvailability startAvailability,
    List<DialogueResponse> dialogue) {

  public MatchResponse {
    moves = moves != null ? List.copyOf(moves) : List.of();
    dialogue = dialogue != null ? List.copyOf(dialogue) : List.of();
  }
}
