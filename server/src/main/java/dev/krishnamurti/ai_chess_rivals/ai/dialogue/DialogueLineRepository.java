package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DialogueLineRepository extends JpaRepository<DialogueLineEntity, Long> {

  Optional<DialogueLineEntity> findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
      UUID matchId, DialogueTriggerType triggerType, int triggerPly, String personalityKey);

  List<DialogueLineEntity> findAllByMatchIdOrderByIdAsc(UUID matchId);

  List<DialogueLineEntity> findTop4ByMatchIdOrderByIdDesc(UUID matchId);
}
