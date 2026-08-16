package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonality;
import dev.krishnamurti.ai_chess_rivals.ai.api.SelectablePersonalityCatalog;
import dev.krishnamurti.ai_chess_rivals.game.TestMatchFixtures;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class MatchControlServiceTest {

  private MatchEngine matchEngine;
  private ExecutorService executorService;
  private MatchControlService service;
  private MatchExecutionGuard guard;
  private MatchDialogueCoordinator matchDialogueCoordinator;
  private SelectablePersonalityCatalog personalityCatalog;
  private Match match;
  private Future<Object> activeTask;

  @BeforeEach
  void setUp() {
    matchEngine = mock(MatchEngine.class);
    executorService = mock(ExecutorService.class);
    activeTask = mock(Future.class);
    doReturn(activeTask).when(executorService).submit(any(Runnable.class));
    guard = new MatchExecutionGuard(fixedClock(), Duration.ZERO, 12);
    matchDialogueCoordinator = mock(MatchDialogueCoordinator.class);
    personalityCatalog = mock(SelectablePersonalityCatalog.class);
    allowTestRivalry();
    service =
        new MatchControlService(
            matchEngine, matchDialogueCoordinator, personalityCatalog, executorService, guard);
    match = TestMatchFixtures.newMatch();
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
    when(matchEngine.startNewMatch(any())).thenReturn(match);

    MatchSnapshot snapshot = service.startMatch("white-test", "black-test");

    assertTrue(snapshot.running());
    assertEquals(match, snapshot.match());

    verify(matchEngine).startNewMatch(TestMatchFixtures.TEST_RIVALRY);
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  void startMatchRejectsMissingPersonalitySelectionsBeforeCreatingMatch() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));

    InvalidPersonalitySelectionException error =
        assertThrows(
            InvalidPersonalitySelectionException.class, () -> service.startMatch("", "black-test"));

    assertEquals("White and Black personality selections are required.", error.getMessage());
    verify(matchEngine, never()).startNewMatch(any());
    verifyNoInteractions(personalityCatalog);
  }

  @Test
  void startMatchRejectsDuplicatePersonalitiesBeforeCatalogLookup() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));

    InvalidPersonalitySelectionException error =
        assertThrows(
            InvalidPersonalitySelectionException.class,
            () -> service.startMatch("white-test", "white-test"));

    assertEquals("White and Black personalities must be distinct.", error.getMessage());
    verify(matchEngine, never()).startNewMatch(any());
    verifyNoInteractions(personalityCatalog);
  }

  @Test
  void startMatchRejectsUnknownWhitePersonality() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));
    when(personalityCatalog.findSelectable("missing-white")).thenReturn(Optional.empty());

    InvalidPersonalitySelectionException error =
        assertThrows(
            InvalidPersonalitySelectionException.class,
            () -> service.startMatch("missing-white", "black-test"));

    assertEquals("Unknown or inactive White personality: missing-white", error.getMessage());
    verify(matchEngine, never()).startNewMatch(any());
  }

  @Test
  void startMatchRejectsUnknownBlackPersonality() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));
    when(personalityCatalog.findSelectable("missing-black")).thenReturn(Optional.empty());

    InvalidPersonalitySelectionException error =
        assertThrows(
            InvalidPersonalitySelectionException.class,
            () -> service.startMatch("white-test", "missing-black"));

    assertEquals("Unknown or inactive Black personality: missing-black", error.getMessage());
    verify(matchEngine, never()).startNewMatch(any());
  }

  @Test
  void startMatchWhenStoppedInProgressResumesWithoutStartingNew() {
    // Stopped in-progress match exists
    when(matchEngine.currentMatch()).thenReturn(match);

    MatchSnapshot snapshot = service.startMatch("white-test", "black-test");

    assertTrue(snapshot.running());
    assertEquals(match, snapshot.match());

    verify(matchEngine, never()).startNewMatch(any());
    verify(executorService).submit(any(Runnable.class));
    verifyNoInteractions(personalityCatalog);
  }

  @Test
  void stoppedMatchRejectsChangedPersonalitiesWithoutRosterLookup() {
    when(matchEngine.currentMatch()).thenReturn(match);

    InvalidPersonalitySelectionException error =
        assertThrows(
            InvalidPersonalitySelectionException.class,
            () -> service.startMatch("black-test", "white-test"));

    assertEquals(
        "A stopped match must resume with its original personalities.", error.getMessage());
    verify(matchEngine, never()).startNewMatch(any());
    verify(executorService, never()).submit(any(Runnable.class));
    verifyNoInteractions(personalityCatalog);
  }

  @Test
  void startMatchWhenMatchFinishedCreatesNewMatch() {
    Match finishedMatch = mock(Match.class);
    when(finishedMatch.isFinished()).thenReturn(true);
    when(matchEngine.currentMatch()).thenReturn(finishedMatch);
    when(matchEngine.startNewMatch(any())).thenReturn(match);

    MatchSnapshot snapshot = service.startMatch("white-test", "black-test");

    assertTrue(snapshot.running());
    assertEquals(match, snapshot.match());

    verify(matchEngine).startNewMatch(TestMatchFixtures.TEST_RIVALRY);
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  void startMatchWhenAlreadyRunningThrowsConflict() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch("white-test", "black-test"); // Sets running to true

    assertThrows(
        MatchConflictException.class, () -> service.startMatch("white-test", "black-test"));
  }

  @Test
  void stopMatchDelegatesToStopCurrentAndReturnsStoppedSnapshot() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch("white-test", "black-test");

    MatchSnapshot snapshot = service.stopMatch();
    MatchSnapshot restartedSnapshot =
        assertDoesNotThrow(() -> service.startMatch("white-test", "black-test"));
    InOrder order = inOrder(matchEngine, activeTask);

    assertFalse(snapshot.running());
    assertTrue(restartedSnapshot.running());
    assertEquals(match, restartedSnapshot.match());
    order.verify(matchEngine).stopCurrentMatch();
    order.verify(activeTask).cancel(false);
    verify(executorService, never()).shutdown();
  }

  @Test
  void repeatedStopIsAnIdempotentNoOp() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch("white-test", "black-test");

    MatchSnapshot first = service.stopMatch();
    MatchSnapshot second = assertDoesNotThrow(service::stopMatch);

    assertFalse(first.running());
    assertFalse(second.running());
    verify(matchEngine, times(1)).stopCurrentMatch();
    verify(activeTask, times(1)).cancel(false);
  }

  @Test
  void stopBeforeAnyMatchReturnsNotFound() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));

    assertThrows(MatchNotFoundException.class, () -> service.stopMatch());
  }

  @Test
  void runningStateResetsOnSuccessfulBackgroundFinish() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch("white-test", "black-test");

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
  void successfulBackgroundFinishStartsCooldown() {
    MatchExecutionGuard cooldownGuard =
        new MatchExecutionGuard(fixedClock(), Duration.ofSeconds(60), 12);
    service =
        new MatchControlService(
            matchEngine,
            matchDialogueCoordinator,
            personalityCatalog,
            executorService,
            cooldownGuard);
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch("white-test", "black-test");

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executorService).submit(runnableCaptor.capture());
    runnableCaptor.getValue().run();

    assertEquals(
        MatchStartBlockReason.MATCH_COOLDOWN_ACTIVE,
        service.currentMatch().startAvailability().blockedBy());
  }

  @Test
  void runningStateResetsOnBackgroundException() {
    when(matchEngine.currentMatch()).thenReturn(match);
    service.startMatch("white-test", "black-test");

    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(executorService).submit(runnableCaptor.capture());

    when(matchEngine.playUntilFinished()).thenThrow(new RuntimeException("Background failure"));

    Runnable task = runnableCaptor.getValue();
    task.run(); // execute background task

    // Service should reset running flag despite exception
    MatchSnapshot snapshot = service.currentMatch();
    assertFalse(snapshot.running());
    assertTrue(snapshot.startAvailability().allowed());
  }

  @Test
  void staleBackgroundTaskDoesNotClearRunningStateForRestartedMatch() {
    when(matchEngine.currentMatch()).thenReturn(match);

    service.startMatch("white-test", "black-test");
    service.stopMatch();
    service.startMatch("white-test", "black-test");

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
    service.startMatch("white-test", "black-test");

    MatchSnapshot snapshot = assertDoesNotThrow(() -> service.stopMatch());

    assertFalse(snapshot.running());
    verify(matchEngine).stopCurrentMatch();
    verify(activeTask).cancel(false);
  }

  @Test
  void synchronousStartFailureRollsBackReservationAndQuota() {
    when(matchEngine.currentMatch()).thenThrow(new IllegalStateException("No match"));
    when(matchEngine.startNewMatch(any()))
        .thenThrow(new IllegalStateException("Cannot initialize"));

    assertThrows(IllegalStateException.class, () -> service.startMatch("white-test", "black-test"));

    assertFalse(guard.isRunning());
    assertEquals(0, guard.availability().dailyStartsAccepted());
  }

  @Test
  void stopWaitsUntilStartHasPublishedItsBackgroundTask() throws Exception {
    CountDownLatch startInsideEngine = new CountDownLatch(1);
    CountDownLatch releaseStart = new CountDownLatch(1);
    CountDownLatch stopAttempted = new CountDownLatch(1);
    AtomicBoolean firstRead = new AtomicBoolean(true);
    when(matchEngine.currentMatch())
        .thenAnswer(
            invocation -> {
              if (firstRead.compareAndSet(true, false)) {
                startInsideEngine.countDown();
                assertTrue(releaseStart.await(2, TimeUnit.SECONDS));
              }
              return match;
            });

    CompletableFuture<MatchSnapshot> start =
        CompletableFuture.supplyAsync(() -> service.startMatch("white-test", "black-test"));
    assertTrue(startInsideEngine.await(2, TimeUnit.SECONDS));
    CompletableFuture<MatchSnapshot> stop =
        CompletableFuture.supplyAsync(
            () -> {
              stopAttempted.countDown();
              return service.stopMatch();
            });
    assertTrue(stopAttempted.await(2, TimeUnit.SECONDS));

    assertThrows(TimeoutException.class, () -> stop.get(200, TimeUnit.MILLISECONDS));
    verify(matchEngine, never()).stopCurrentMatch();

    releaseStart.countDown();
    assertTrue(start.get(2, TimeUnit.SECONDS).running());
    assertFalse(stop.get(2, TimeUnit.SECONDS).running());
    verify(matchEngine).stopCurrentMatch();
    verify(activeTask).cancel(false);
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC);
  }

  private void allowTestRivalry() {
    when(personalityCatalog.findSelectable("white-test"))
        .thenReturn(Optional.of(new SelectablePersonality("white-test", "White Test")));
    when(personalityCatalog.findSelectable("black-test"))
        .thenReturn(Optional.of(new SelectablePersonality("black-test", "Black Test")));
  }
}
