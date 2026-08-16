package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.dialogue.DialogueRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.owner.control-token=test-owner-token",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
@Import({PersonalityRepositoryTestConfiguration.class, DialogueRepositoryTestConfiguration.class})
class MatchWebSocketIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private MatchWebSocketHandler handler;

  @MockitoBean private MatchControlService matchControlService;

  @MockitoBean private StockfishClient stockfishClient;

  @Test
  void websocketEndpointSendsNoMatchThenAcceptsLaterBroadcast() throws Exception {
    when(matchControlService.currentMatch()).thenThrow(new MatchNotFoundException("No match"));

    StandardWebSocketClient client = new StandardWebSocketClient();
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    WebSocketSession session =
        client
            .execute(
                new TextWebSocketHandler() {
                  @Override
                  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    messages.add(message.getPayload());
                  }
                },
                "ws://localhost:" + port + "/ws/match")
            .get(5, TimeUnit.SECONDS);

    String noMatchMessage = messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(noMatchMessage);
    assertTrue(noMatchMessage.contains("\"type\":\"NO_MATCH\""));

    handler.broadcast(
        new MatchStreamMessage<>(
            MatchStreamMessageType.MATCH_STARTED,
            new MatchStartedMessage(
                UUID.randomUUID(), PlayerColor.WHITE, BoardPosition.STARTING_POSITION.fen())));

    String matchStartedMessage = messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(matchStartedMessage);
    assertTrue(matchStartedMessage.contains("\"type\":\"MATCH_STARTED\""));
    assertTrue(matchStartedMessage.contains("\"sideToMove\":\"WHITE\""));
    assertTrue(
        matchStartedMessage.contains("\"fen\":\"" + BoardPosition.STARTING_POSITION.fen() + "\""));
    assertTrue(session.isOpen());
    session.close();
  }

  @Test
  void websocketEndpointHydratesPersistedDialogueInChronologicalOrder() throws Exception {
    Instant createdAt = Instant.parse("2026-08-16T00:00:00Z");
    Match match = Match.newGame();
    UUID matchId = match.id();
    when(matchControlService.currentMatch())
        .thenReturn(
            new MatchSnapshot(
                match,
                false,
                new MatchStartAvailability(true, null, 0, 1, 10),
                List.of(
                    newPersistedDialogue(1, matchId, "older line", createdAt),
                    newPersistedDialogue(2, matchId, "newer line", createdAt.plusSeconds(1)))));

    StandardWebSocketClient client = new StandardWebSocketClient();
    BlockingQueue<String> messages = new LinkedBlockingQueue<>();

    WebSocketSession session =
        client
            .execute(
                new TextWebSocketHandler() {
                  @Override
                  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    messages.add(message.getPayload());
                  }
                },
                "ws://localhost:" + port + "/ws/match")
            .get(5, TimeUnit.SECONDS);

    String stateMessage = messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(stateMessage);
    assertTrue(stateMessage.contains("\"type\":\"MATCH_STATE\""));
    assertTrue(stateMessage.contains("\"matchId\":\"" + matchId + "\""));
    assertTrue(stateMessage.indexOf("older line") < stateMessage.indexOf("newer line"));
    assertEquals(1, countOccurrences(stateMessage, "older line"));
    assertEquals(1, countOccurrences(stateMessage, "newer line"));
    session.close();
  }

  private static PersistedDialogue newPersistedDialogue(
      long id, UUID matchId, String text, Instant createdAt) {
    return new PersistedDialogue(
        (int) id,
        matchId,
        DialogueTriggerType.MOVE,
        (int) id,
        id == 1 ? "blaze" : "vesper",
        id == 1 ? "Blaze" : "Vesper",
        text,
        DialogueEmotion.CONFIDENT,
        DialogueReactionType.MOVE_REACTION,
        AiResponseSource.DETERMINISTIC_FALLBACK,
        createdAt);
  }

  private static int countOccurrences(String value, String token) {
    return (value.length() - value.replace(token, "").length()) / token.length();
  }
}
