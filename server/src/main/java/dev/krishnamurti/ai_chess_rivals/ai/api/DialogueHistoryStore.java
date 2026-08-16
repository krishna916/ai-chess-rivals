package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DialogueHistoryStore {

  Optional<PersistedDialogue> persistIfAbsent(
      UUID matchId, DialogueTriggerType triggerType, int triggerPly, GeneratedDialogue dialogue);

  List<PersistedDialogue> findAll(UUID matchId);

  List<DialogueHistoryLine> lastFour(UUID matchId);
}
