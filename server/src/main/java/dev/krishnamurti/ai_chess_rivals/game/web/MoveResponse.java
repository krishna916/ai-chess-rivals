package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;

public record MoveResponse(
    int sequenceNumber,
    PlayerColor player,
    String notation,
    String fenAfterMove) {}
