package dev.krishnamurti.ai_chess_rivals.game.application;

interface MatchPacing {
  void waitBeforeNextMove() throws InterruptedException;
}
