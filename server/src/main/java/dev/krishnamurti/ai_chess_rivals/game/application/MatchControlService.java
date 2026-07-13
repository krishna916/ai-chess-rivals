package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
  private final MatchExecutionGuard executionGuard;
  private final AtomicReference<Future<?>> activeTask = new AtomicReference<>();
  private final AtomicLong activeTaskId = new AtomicLong();
  private final AtomicLong nextTaskId = new AtomicLong();

  public MatchControlService(
      MatchEngine matchEngine,
      @Qualifier("matchExecutor") ExecutorService matchExecutor,
      MatchExecutionGuard executionGuard) {
    this.matchEngine = matchEngine;
    this.matchExecutor = matchExecutor;
    this.executionGuard = executionGuard;
  }

  public synchronized MatchSnapshot startMatch() {
    MatchExecutionGuard.StartReservation reservation = executionGuard.reserveStart();

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
                boolean completedNormally = false;
                try {
                  matchEngine.playUntilFinished();
                  completedNormally = true;
                } catch (Exception e) {
                  int ply = 0;
                  try {
                    ply = matchEngine.currentMatch().moveCount() + 1;
                  } catch (Exception stateReadFailure) {
                    log.debug(
                        "Could not read match state after background failure", stateReadFailure);
                  }
                  log.error("Unexpected background failure in match execution at ply {}", ply, e);
                } finally {
                  if (activeTaskId.compareAndSet(taskId, 0)) {
                    activeTask.set(null);
                    if (completedNormally) {
                      executionGuard.markFinished();
                    } else {
                      executionGuard.abortExecution();
                    }
                  }
                }
              });
      activeTask.set(submittedTask);
      if (submittedTask.isDone() && activeTaskId.get() == 0) {
        activeTask.compareAndSet(submittedTask, null);
      }

      return snapshot(match);
    } catch (RuntimeException | Error e) {
      activeTask.set(null);
      activeTaskId.set(0);
      executionGuard.abortStart(reservation);
      throw e;
    }
  }

  public synchronized MatchSnapshot stopMatch() {
    if (executionGuard.markStopped()) {
      try {
        matchEngine.stopCurrentMatch();
      } finally {
        Future<?> task = activeTask.getAndSet(null);
        activeTaskId.set(0);
        if (task != null) {
          task.cancel(false);
        }
      }
    }
    return currentMatch();
  }

  public MatchSnapshot currentMatch() {
    try {
      return snapshot(matchEngine.currentMatch());
    } catch (IllegalStateException e) {
      throw new MatchNotFoundException("No match has been started", e);
    }
  }

  private MatchSnapshot snapshot(Match match) {
    return new MatchSnapshot(match, executionGuard.isRunning(), executionGuard.availability());
  }
}
