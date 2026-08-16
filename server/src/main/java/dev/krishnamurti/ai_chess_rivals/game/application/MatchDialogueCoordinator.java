package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueGenerator;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryStore;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueStartRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.MatchRivalry;
import dev.krishnamurti.ai_chess_rivals.game.event.DialoguePlayed;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEventSink;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
final class MatchDialogueCoordinator {

  private final DialogueGenerator dialogueGenerator;
  private final DialogueHistoryStore historyStore;
  private final MatchEventSink matchEventSink;

  MatchDialogueCoordinator(
      DialogueGenerator dialogueGenerator,
      DialogueHistoryStore historyStore,
      MatchEventSink matchEventSink) {
    this.dialogueGenerator =
        Objects.requireNonNull(dialogueGenerator, "dialogueGenerator must not be null");
    this.historyStore = Objects.requireNonNull(historyStore, "historyStore must not be null");
    this.matchEventSink = Objects.requireNonNull(matchEventSink, "matchEventSink must not be null");
  }

  void onGameStart(UUID matchId, MatchRivalry rivalry, BooleanSupplier authoritative) {
    safeRun(
        matchId,
        0,
        () -> {
          List<GeneratedDialogue> generated =
              dialogueGenerator.generateStart(
                  new DialogueStartRequest(
                      rivalry.whiteKey(), rivalry.blackKey(), historyStore.lastFour(matchId)));
          persistAndPublish(matchId, DialogueTriggerType.GAME_START, 0, generated, authoritative);
        });
  }

  void onMove(UUID matchId, MatchRivalry rivalry, MovePlayed move, BooleanSupplier authoritative) {
    safeRun(
        matchId,
        move.ply(),
        () -> {
          String mover = rivalry.personalityKey(move.player());
          String opponent = rivalry.personalityKey(move.player().opposite());
          DialogueMoveRequest request =
              new DialogueMoveRequest(
                  move.ply(),
                  mover,
                  opponent,
                  move.notation().value(),
                  move.capture(),
                  move.check(),
                  move.checkmate(),
                  move.promotion(),
                  move.evaluation(),
                  historyStore.lastFour(matchId));

          dialogueGenerator
              .generateMove(request)
              .ifPresent(
                  generated ->
                      persistAndPublish(
                          matchId,
                          DialogueTriggerType.MOVE,
                          move.ply(),
                          List.of(generated),
                          authoritative));
        });
  }

  void onGameEnd(
      UUID matchId,
      MatchRivalry rivalry,
      GameResult result,
      int totalPlies,
      BooleanSupplier authoritative) {
    safeRun(
        matchId,
        totalPlies,
        () -> {
          DialogueOutcome whiteOutcome =
              switch (result) {
                case WHITE_WINS -> DialogueOutcome.VICTORY;
                case BLACK_WINS -> DialogueOutcome.DEFEAT;
                case DRAW -> DialogueOutcome.DRAW;
              };
          DialogueOutcome blackOutcome =
              switch (result) {
                case WHITE_WINS -> DialogueOutcome.DEFEAT;
                case BLACK_WINS -> DialogueOutcome.VICTORY;
                case DRAW -> DialogueOutcome.DRAW;
              };
          List<GeneratedDialogue> generated =
              dialogueGenerator.generateEnd(
                  new DialogueEndRequest(
                      rivalry.whiteKey(),
                      whiteOutcome,
                      rivalry.blackKey(),
                      blackOutcome,
                      totalPlies,
                      historyStore.lastFour(matchId)));
          persistAndPublish(
              matchId, DialogueTriggerType.GAME_END, totalPlies, generated, authoritative);
        });
  }

  List<PersistedDialogue> history(UUID matchId) {
    try {
      return historyStore.findAll(matchId);
    } catch (RuntimeException exception) {
      log.warn("Dialogue history unavailable for match {}", matchId, exception);
      return List.of();
    }
  }

  private void persistAndPublish(
      UUID matchId,
      DialogueTriggerType triggerType,
      int triggerPly,
      List<GeneratedDialogue> generated,
      BooleanSupplier authoritative) {
    for (GeneratedDialogue line : generated) {
      if (!authoritative.getAsBoolean()) {
        log.debug(
            "Discarding stale dialogue for match {} at {} ply {}",
            matchId,
            triggerType,
            triggerPly);
        return;
      }
      historyStore
          .persistIfAbsent(matchId, triggerType, triggerPly, line)
          .ifPresent(saved -> matchEventSink.publish(new DialoguePlayed(saved)));
    }
  }

  private void safeRun(UUID matchId, int triggerPly, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      log.warn("Dialogue unavailable for match {} at ply {}", matchId, triggerPly, exception);
    }
  }
}
