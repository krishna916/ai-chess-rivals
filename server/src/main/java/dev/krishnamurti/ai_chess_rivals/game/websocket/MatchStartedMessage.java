package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;

public record MatchStartedMessage(PlayerColor sideToMove, String fen) {}
