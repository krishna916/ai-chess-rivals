package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;

public record MatchStoppedMessage(PlayerColor sideToMove, String fen, int totalPlies) {}
