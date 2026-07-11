package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;

public record MatchFinishedMessage(GameResult result, String fen, int totalPlies) {}
