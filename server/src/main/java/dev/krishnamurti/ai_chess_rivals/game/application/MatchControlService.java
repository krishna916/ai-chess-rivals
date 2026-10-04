package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonality;
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonalityCatalog;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MatchRivalry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public final class MatchControlService {

  private final MatchEngine matchEngine;
  private final MatchDialogueCoordinator matchDialogueCoordinator;
  private final SelectablePersonalityCatalog personalityCatalog;
  private final ExecutorService matchExecutor;
  private final MatchExecutionGuard executionGuard;
  private final AtomicReference<Future<?>> activeTask = new AtomicReference<>();
  private final AtomicLong activeTaskId = new AtomicLong();
  private final AtomicLong nextTaskId = new AtomicLong();

  public MatchControlService(
      MatchEngine matchEngine,
      MatchDialogueCoordinator matchDialogueCoordinator,
      SelectablePersonalityCatalog personalityCatalog,
      @Qualifier("matchExecutor") ExecutorService matchExecutor,
      MatchExecutionGuard executionGuard) {
    this.matchEngine = matchEngine;
    this.matchDialogueCoordinator = matchDialogueCoordinator;
    this.personalityCatalog = personalityCatalog;
    this.matchExecutor = matchExecutor;
    this.executionGuard = executionGuard;
  }

  public synchronized MatchSnapshot startMatch(
      String whitePersonalityKey, String blackPersonalityKey) {
    MatchExecutionGuard.StartReservation reservation;
    try {
      reservation = executionGuard.reserveStart();
    } catch (RuntimeException exception) {
      log.info("event=match.start_rejected outcome=execution_guard");
      throw exception;
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
        MatchRivalry rivalry = resolveNewRivalry(whitePersonalityKey, blackPersonalityKey);
        match = matchEngine.startNewMatch(rivalry);
        log.info("event=match.created matchId={}", match.id());
      } else {
        requireSameRivalry(match.rivalry(), whitePersonalityKey, blackPersonalityKey);
        log.info("event=match.resumed matchId={}", match.id());
      }

      String requestId = MDC.get("requestId");
      if (requestId == null) {
        requestId = UUID.randomUUID().toString();
      }
      UUID matchId = match.id();
      long taskId = nextTaskId.incrementAndGet();
      activeTaskId.set(taskId);
      Future<?> submittedTask =
          matchExecutor.submit(
              tracedMatchTask(
                  () -> {
                    log.info("event=match.worker_started matchId={}", matchId);
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
                            "Could not read match state after background failure",
                            stateReadFailure);
                      }
                      log.error(
                          "Unexpected background failure in match execution at ply {}", ply, e);
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
                    log.info(
                        "event=match.worker_completed matchId={} outcome={}",
                        matchId,
                        completedNormally ? "success" : "failed");
                  },
                  requestId,
                  matchId));
      log.info("event=match.worker_submitted matchId={} taskId={}", matchId, taskId);
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
      log.info("event=match.stop outcome=applied");
      try {
        matchEngine.stopCurrentMatch();
      } finally {
        Future<?> task = activeTask.getAndSet(null);
        activeTaskId.set(0);
        if (task != null) {
          task.cancel(false);
        }
      }
    } else {
      MatchSnapshot snapshot = currentMatch();
      log.info("event=match.stop outcome=already_stopped matchId={}", snapshot.match().id());
      return snapshot;
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
    long startedAtNanos = System.nanoTime();
    MatchSnapshot snapshot =
        new MatchSnapshot(
            match,
            executionGuard.isRunning(),
            executionGuard.availability(),
            matchDialogueCoordinator.history(match.id()));
    log.info(
        "event=match.snapshot matchId={} historyCount={} durationMs={}",
        match.id(),
        snapshot.dialogue().size(),
        elapsedMillis(startedAtNanos));
    return snapshot;
  }

  private static Runnable tracedMatchTask(Runnable task, String requestId, UUID matchId) {
    return () -> {
      Map<String, String> previousMdc = MDC.getCopyOfContextMap();
      MDC.put("requestId", requestId);
      MDC.put("matchId", matchId.toString());
      try {
        task.run();
      } finally {
        if (previousMdc == null) {
          MDC.clear();
        } else {
          MDC.setContextMap(previousMdc);
        }
      }
    };
  }

  private static long elapsedMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000L;
  }

  private MatchRivalry resolveNewRivalry(String whiteKey, String blackKey) {
    if (whiteKey == null || whiteKey.isBlank() || blackKey == null || blackKey.isBlank()) {
      throw new InvalidPersonalitySelectionException(
          "White and Black personality selections are required.");
    }
    if (whiteKey.equals(blackKey)) {
      throw new InvalidPersonalitySelectionException(
          "White and Black personalities must be distinct.");
    }

    SelectablePersonality white =
        personalityCatalog
            .findSelectable(whiteKey)
            .orElseThrow(
                () ->
                    new InvalidPersonalitySelectionException(
                        "Unknown or inactive White personality: " + whiteKey));
    SelectablePersonality black =
        personalityCatalog
            .findSelectable(blackKey)
            .orElseThrow(
                () ->
                    new InvalidPersonalitySelectionException(
                        "Unknown or inactive Black personality: " + blackKey));

    return new MatchRivalry(white.key(), white.displayName(), black.key(), black.displayName());
  }

  private void requireSameRivalry(MatchRivalry rivalry, String whiteKey, String blackKey) {
    if (!rivalry.whiteKey().equals(whiteKey) || !rivalry.blackKey().equals(blackKey)) {
      throw new InvalidPersonalitySelectionException(
          "A stopped match must resume with its original personalities.");
    }
  }
}
