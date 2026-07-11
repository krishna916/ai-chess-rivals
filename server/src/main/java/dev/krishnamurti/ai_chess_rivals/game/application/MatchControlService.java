package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public final class MatchControlService {

  private final MatchEngine matchEngine;
  private final ExecutorService matchExecutor;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public MatchControlService(
      MatchEngine matchEngine,
      @Qualifier("matchExecutor") ExecutorService matchExecutor) {
    this.matchEngine = matchEngine;
    this.matchExecutor = matchExecutor;
  }

  public MatchSnapshot startMatch() {
    if (!running.compareAndSet(false, true)) {
      throw new MatchConflictException("Match is already running");
    }

    try {
      Match match = null;
      boolean hasMatch = false;
      try {
        match = matchEngine.currentMatch();
        hasMatch = true;
      } catch (IllegalStateException e) {
        // No match exists
      }

      if (!hasMatch || match.isFinished()) {
        match = matchEngine.startNewMatch();
      }

      final Match finalMatch = match;
      matchExecutor.submit(
          () -> {
            try {
              matchEngine.playUntilFinished();
            } catch (Exception e) {
              int ply = 0;
              try {
                ply = matchEngine.currentMatch().moveCount() + 1;
              } catch (Exception ignored) {
              }
              log.error("Unexpected background failure in match execution at ply {}", ply, e);
            } finally {
              running.set(false);
            }
          });

      return new MatchSnapshot(finalMatch, true);
    } catch (Exception e) {
      running.set(false);
      throw e;
    }
  }

  public MatchSnapshot stopMatch() {
    if (!running.get()) {
      throw new MatchConflictException("No match is currently running");
    }

    matchEngine.stopCurrentMatch();
    return new MatchSnapshot(matchEngine.currentMatch(), false);
  }

  public MatchSnapshot currentMatch() {
    try {
      Match match = matchEngine.currentMatch();
      return new MatchSnapshot(match, running.get());
    } catch (IllegalStateException e) {
      throw new MatchNotFoundException("No match has been started", e);
    }
  }
}
