package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.web.DialogueResponse;
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
import dev.krishnamurti.ai_chess_rivals.game.web.MoveResponse;
import java.util.List;
import java.util.UUID;

record MatchStateMessage(
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

  public MatchStateMessage {
    moves = moves != null ? List.copyOf(moves) : List.of();
    dialogue = dialogue != null ? List.copyOf(dialogue) : List.of();
  }

  public static MatchStateMessage from(MatchSnapshot snapshot) {
    Match match = snapshot.match();
    List<MoveResponse> moves = match.moves().stream().map(MoveResponse::from).toList();
    List<DialogueResponse> dialogue =
        snapshot.dialogue().stream().map(DialogueResponse::from).toList();
    MatchPersonalityResponse whitePersonality =
        new MatchPersonalityResponse(
            match.rivalry().whiteKey(), match.rivalry().whiteDisplayName());
    MatchPersonalityResponse blackPersonality =
        new MatchPersonalityResponse(
            match.rivalry().blackKey(), match.rivalry().blackDisplayName());

    return new MatchStateMessage(
        match.id(),
        whitePersonality,
        blackPersonality,
        match.sideToMove(),
        match.currentPosition().fen(),
        moves,
        match.status(),
        match.result().orElse(null),
        snapshot.running(),
        snapshot.startAvailability(),
        dialogue);
  }
}
