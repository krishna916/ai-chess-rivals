package dev.krishnamurti.ai_chess_rivals.game.event;

public sealed interface MatchEvent permits MatchStarted, MovePlayed, MatchFinished {}
