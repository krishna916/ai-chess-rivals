package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class MatchControlServiceTest {

  private MatchEngine matchEngine;
  private ExecutorService executorService;
  private MatchControlService service;
  private Match match;
  private Future<Object> activeTask;

  @BeforeEach
  void setUp() {
    matchEngine = mock(MatchEngine.class);
    executorService = mock(ExecutorService.class);
    activeTask = mock(Future.class);
    doReturn(activeTask).when(executorService).submit(any(Runnable.class));
    service = new MatchControlService(matchEngine, executorService);
    match = Match.newGame();
  }

  @Test
  void currentMatchThrowsWhenNoMatchStarted() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));
    assertThrows(MatchNotFoundException.class, () -> service.currentMatch());
  }

  @Test
  void currentMatchReturnsStateWhenStarted() {
    when(matchEngine.currentMatch()).thenReturn(match);
    MatchSnapshot snapshot = service.currentMatch();
    assertEquals(match, snapshot.match());
    assertFalse(snapshot.running());
  }

  @Test
  void startMatchWhenNoMatchExistsCreatesAndRunsMatch() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));
    when(matchEngine.startNewMatch()).thenReturn(match);

    MatchSnapshot snapshot = service.startMatch();

    assertTrue(snapshot.running());
    assertEquals(match, snapshot.match());

    verify(matchEngine).startNewMatch();
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  void startMatchWhenStoppedInProgressResumesWithoutStartingNew() {
    // Stopped in-progress match exists
    when(matchEngine.currentMatch()).thenReturn(match);

    MatchSnapshot snapshot = service.startMatch();

    assertTrue(snapshot.running());
    assertEquals(match, snapshot.match());

    verify(matchEngine, never()).startNewMatch();
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  void startMatchWhenMatchFinishedCreatesNewMatch() {
    Match finishedMatch = mock(Match.class);
    when(finishedMatch.isFinished()).thenReturn(true);
    when(matchEngine.currentMatch()).thenReturn(finishedMatch);
    when(matchEngine.startNewMatch()).thenReturn(match);

    MatchSnapshot snapshot = service.startMatch();

    assertTrue(snapshot.running());
    assertEquals(match, snapshot.match());

    verify(matchEngine).startNewMatch();
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  void startMatchWhenAlreadyRunningThrowsConflict() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch(); // Sets running to true

    assertThrows(MatchConflictException.class, () -> service.startMatch());
  }

  @Test
  void stopMatchDelegatesToStopCurrentAndReturnsStoppedSnapshot() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch();

    MatchSnapshot snapshot = service.stopMatch();
    MatchSnapshot restartedSnapshot = assertDoesNotThrow(() -> service.startMatch());
    InOrder order = inOrder(matchEngine, activeTask);

    assertFalse(snapshot.running());
    assertTrue(restartedSnapshot.running());
    assertEquals(match, restartedSnapshot.match());
    order.verify(matchEngine).stopCurrentMatch();
    order.verify(activeTask).cancel(false);
    verify(executorService, never()).shutdown();
  }

  @Test
  void stopMatchWhenNotRunningThrowsConflict() {
    assertThrows(MatchConflictException.class, () -> service.stopMatch());
  }

  @Test
  void runningStateResetsOnSuccessfulBackgroundFinish() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch();

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executorService).submit(runnableCaptor.capture());

    Runnable task = runnableCaptor.getValue();
    task.run(); // execute background task

    verify(matchEngine).playUntilFinished();
    // Service should no longer be running
    MatchSnapshot snapshot = service.currentMatch();
    assertFalse(snapshot.running());
  }

  @Test
  void runningStateResetsOnBackgroundException() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch();

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executorService).submit(runnableCaptor.capture());

    when(matchEngine.playUntilFinished()).thenThrow(new RuntimeException("Background failure"));

    Runnable task = runnableCaptor.getValue();
    task.run(); // execute background task

    // Service should reset running flag despite exception
    MatchSnapshot snapshot = service.currentMatch();
    assertFalse(snapshot.running());
  }

  @Test
  void staleBackgroundTaskDoesNotClearRunningStateForRestartedMatch() {
    when(matchEngine.currentMatch()).thenReturn(match);

    service.startMatch();
    service.stopMatch();
    service.startMatch();

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executorService, times(2)).submit(runnableCaptor.capture());

    Runnable firstTask = runnableCaptor.getAllValues().getFirst();
    firstTask.run();

    MatchSnapshot snapshot = service.currentMatch();
    assertTrue(snapshot.running());
  }

  @Test
  void stopMatchCancelsEvenWhenTheActiveTaskHasAlreadyCompleted() {
    when(matchEngine.currentMatch()).thenReturn(match);
    when(activeTask.isDone()).thenReturn(true);
    service.startMatch();

    MatchSnapshot snapshot = assertDoesNotThrow(() -> service.stopMatch());

    assertFalse(snapshot.running());
    verify(matchEngine).stopCurrentMatch();
    verify(activeTask).cancel(false);
  }
}
