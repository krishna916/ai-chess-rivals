package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchEngine;
import java.util.concurrent.ExecutorService;
import org.springframework.stereotype.Service;

@Service
public final class MatchControlService {

  private final MatchEngine matchEngine;
  private final ExecutorService matchExecutor;

  public MatchControlService(MatchEngine matchEngine, ExecutorService matchExecutor) {
    this.matchEngine = matchEngine;
    this.matchExecutor = matchExecutor;
  }

  public MatchSnapshot startMatch() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public MatchSnapshot stopMatch() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public MatchSnapshot currentMatch() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
