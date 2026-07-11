package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public final class MatchControlService {

  private final MatchEngine matchEngine;
  private final ExecutorService matchExecutor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicReference<Future<?>> activeTask = new AtomicReference<>();
  private final AtomicLong activeTaskId = new AtomicLong();
  private final AtomicLong nextTaskId = new AtomicLong();

  public MatchControlService(
      MatchEngine matchEngine, @Qualifier("matchExecutor") ExecutorService matchExecutor) {
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

      long taskId = nextTaskId.incrementAndGet();
      activeTaskId.set(taskId);
      Future<?> submittedTask =
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
                  if (activeTaskId.compareAndSet(taskId, 0)) {
                    activeTask.set(null);
                    running.set(false);
                  }
                }
              });
      activeTask.set(submittedTask);
      if (submittedTask.isDone() && activeTaskId.get() == 0) {
        activeTask.compareAndSet(submittedTask, null);
      }

      return new MatchSnapshot(match, true);
    } catch (Exception e) {
      activeTask.set(null);
      activeTaskId.set(0);
      running.set(false);
      throw e;
    }
  }

  public MatchSnapshot stopMatch() {
    if (!running.compareAndSet(true, false)) {
      throw new MatchConflictException("No match is currently running");
    }

    activeTask.set(null);
    activeTaskId.set(0);
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
