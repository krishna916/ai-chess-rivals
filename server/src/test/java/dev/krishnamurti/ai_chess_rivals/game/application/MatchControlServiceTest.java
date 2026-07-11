package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MatchControlServiceTest {

  private MatchEngine matchEngine;
  private ExecutorService executorService;
  private MatchControlService service;
  private Match match;

  @BeforeEach
  void setUp() {
    matchEngine = mock(MatchEngine.class);
    executorService = mock(ExecutorService.class);
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
    // Setup running state first
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch();

    MatchSnapshot snapshot = service.stopMatch();

    assertFalse(snapshot.running());
    verify(matchEngine).stopCurrentMatch();
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
}
