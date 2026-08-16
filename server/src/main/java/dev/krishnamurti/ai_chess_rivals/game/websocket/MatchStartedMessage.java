package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse;
import java.util.UUID;

public record MatchStartedMessage(
    UUID matchId,
    MatchPersonalityResponse whitePersonality,
    MatchPersonalityResponse blackPersonality,
    PlayerColor sideToMove,
    String fen) {}
