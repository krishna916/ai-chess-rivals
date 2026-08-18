package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.List;

public final class MatchResponseMapper {

  private MatchResponseMapper() {}

  public static MatchResponse map(MatchSnapshot snapshot) {
    Match match = snapshot.match();
    MatchPersonalityResponse whitePersonality =
        new MatchPersonalityResponse(
            match.rivalry().whiteKey(), match.rivalry().whiteDisplayName());
    MatchPersonalityResponse blackPersonality =
        new MatchPersonalityResponse(
            match.rivalry().blackKey(), match.rivalry().blackDisplayName());
    List<MoveResponse> moves = match.moves().stream().map(MoveResponse::from).toList();
    List<DialogueResponse> dialogue =
        snapshot.dialogue().stream().map(DialogueResponse::from).toList();

    return new MatchResponse(
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
