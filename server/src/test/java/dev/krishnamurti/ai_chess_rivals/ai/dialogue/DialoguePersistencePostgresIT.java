package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {
      "spring.datasource.url=jdbc:postgresql://localhost:55433/aichessrivals_it",
      "spring.datasource.username=postgres",
      "spring.datasource.password=secretpassword",
      "spring.flyway.url=jdbc:postgresql://localhost:55433/aichessrivals_it",
      "spring.flyway.user=postgres",
      "spring.flyway.password=secretpassword",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DialoguePersistenceService.class, PersonalityService.class})
class DialoguePersistencePostgresIT {

  @Autowired private DialoguePersistenceService store;

  @Test
  void persistsOnlyOneRowForTheSameTriggerAndSpeaker() {
    UUID matchId = UUID.randomUUID();
    GeneratedDialogue line = generated("blaze", "one line");

    assertTrue(store.persistIfAbsent(matchId, DialogueTriggerType.MOVE, 12, line).isPresent());
    assertFalse(store.persistIfAbsent(matchId, DialogueTriggerType.MOVE, 12, line).isPresent());
    assertEquals(1, store.findAll(matchId).size());
  }

  @Test
  void returnsChronologicalHistoryAndOnlyLastFourContextLines() {
    UUID matchId = UUID.randomUUID();
    for (int ply = 1; ply <= 6; ply++) {
      String personality = ply % 2 == 0 ? "blaze" : "vesper";
      store.persistIfAbsent(
          matchId, DialogueTriggerType.MOVE, ply, generated(personality, "line-" + ply));
    }

    assertEquals(
        java.util.List.of(1, 2, 3, 4, 5, 6),
        store.findAll(matchId).stream().map(PersistedDialogue::triggerPly).toList());
    assertEquals(
        java.util.List.of(3, 4, 5, 6),
        store.lastFour(matchId).stream().map(DialogueHistoryLine::triggeringPly).toList());
  }

  private static GeneratedDialogue generated(String personalityKey, String text) {
    return new GeneratedDialogue(
        personalityKey,
        text,
        DialogueEmotion.CONFIDENT,
        DialogueReactionType.MOVE_REACTION,
        AiResponseSource.DETERMINISTIC_FALLBACK);
  }
}
