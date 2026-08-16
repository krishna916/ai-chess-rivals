package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.web.MoveResponse;
import dev.krishnamurti.ai_chess_rivals.game.web.DialogueResponse;
import java.util.List;
import java.util.UUID;

record MatchStateMessage(
    UUID matchId,
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

    return new MatchStateMessage(
        match.id(),
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
