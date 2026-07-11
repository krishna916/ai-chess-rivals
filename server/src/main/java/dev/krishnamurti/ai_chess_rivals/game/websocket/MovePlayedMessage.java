package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;

public record MovePlayedMessage(
    int ply,
    PlayerColor player,
    String notation,
    String fen,
    boolean capture,
    boolean check,
    boolean checkmate,
    boolean promotion) {}
