package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;

public interface ChessPlayer {

  void startNewGame();

  MoveNotation chooseMove(Match match);
}
