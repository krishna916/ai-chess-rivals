package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.Move;
import java.util.List;

public final class MatchResponseMapper {

  private MatchResponseMapper() {}

  public static MatchResponse map(MatchSnapshot snapshot) {
    Match match = snapshot.match();
    List<MoveResponse> moves = match.moves().stream().map(MatchResponseMapper::mapMove).toList();

    return new MatchResponse(
        match.sideToMove(),
        match.currentPosition().fen(),
        moves,
        match.status(),
        match.result().orElse(null),
        snapshot.running());
  }

  private static MoveResponse mapMove(Move move) {
    return new MoveResponse(
        move.sequenceNumber(),
        move.playedBy(),
        move.notation().value(),
        move.positionAfterMove().fen());
  }
}
