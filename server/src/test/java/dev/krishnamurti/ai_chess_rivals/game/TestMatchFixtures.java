package dev.krishnamurti.ai_chess_rivals.game;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MatchRivalry;

public final class TestMatchFixtures {

  public static final MatchRivalry TEST_RIVALRY =
      new MatchRivalry("white-test", "White Test", "black-test", "Black Test");

  private TestMatchFixtures() {}

  public static Match newMatch() {
    return Match.newGame(TEST_RIVALRY);
  }
}
