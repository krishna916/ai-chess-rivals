package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.UUID;

public record MatchStartedMessage(UUID matchId, PlayerColor sideToMove, String fen) {}
