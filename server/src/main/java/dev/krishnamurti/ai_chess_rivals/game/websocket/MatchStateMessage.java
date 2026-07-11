package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.web.MoveResponse;
import java.util.List;

record MatchStateMessage(
    PlayerColor sideToMove,
    String fen,
    List<MoveResponse> moves,
    GameStatus status,
    GameResult result,
    boolean running) {

  public MatchStateMessage {
    moves = moves != null ? List.copyOf(moves) : List.of();
  }

  public static MatchStateMessage from(MatchSnapshot snapshot) {
    Match match = snapshot.match();
    List<MoveResponse> moves =
        match.moves().stream()
            .map(
                move ->
                    new MoveResponse(
                        move.sequenceNumber(),
                        move.playedBy(),
                        move.notation().value(),
                        move.positionAfterMove().fen()))
            .toList();

    return new MatchStateMessage(
        match.sideToMove(),
        match.currentPosition().fen(),
        moves,
        match.status(),
        match.result().orElse(null),
        snapshot.running());
  }
}
