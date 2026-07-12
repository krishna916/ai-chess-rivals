package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
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
            new MatchStartedMessage(PlayerColor.WHITE, BoardPosition.STARTING_POSITION.fen())));

    String matchStartedMessage = messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(matchStartedMessage);
    assertTrue(matchStartedMessage.contains("\"type\":\"MATCH_STARTED\""));
    assertTrue(matchStartedMessage.contains("\"sideToMove\":\"WHITE\""));
    assertTrue(
        matchStartedMessage.contains("\"fen\":\"" + BoardPosition.STARTING_POSITION.fen() + "\""));
    assertTrue(session.isOpen());
    session.close();
  }
}
