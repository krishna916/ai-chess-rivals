package dev.krishnamurti.ai_chess_rivals.game.application;

public record MatchStartAvailability(
    boolean allowed,
    MatchStartBlockReason blockedBy,
    long retryAfterSeconds,
    int dailyStartsAccepted,
    int dailyStartLimit) {}
