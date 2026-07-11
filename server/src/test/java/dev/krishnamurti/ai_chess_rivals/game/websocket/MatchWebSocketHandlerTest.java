package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class MatchWebSocketHandlerTest {

  private MatchControlService matchControlService;
  private MatchWebSocketHandler handler;

  @BeforeEach
  void setUp() {
    matchControlService = mock(MatchControlService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<MatchControlService> matchControlServiceProvider = mock(ObjectProvider.class);
    when(matchControlServiceProvider.getObject()).thenReturn(matchControlService);
    handler = new MatchWebSocketHandler(new ObjectMapper(), matchControlServiceProvider);
  }

  @Test
  void newClientReceivesCurrentMatchStateImmediately() throws Exception {
    Match match = Match.newGame();
    when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, true));

    StubWebSocketSession rawSession = new StubWebSocketSession("healthy");

    handler.afterConnectionEstablished(rawSession);

    assertEquals(1, rawSession.getSentMessages().size());
    String payload = rawSession.getSentMessages().getFirst().getPayload();
    assertTrue(payload.contains("\"type\":\"MATCH_STATE\""));
    assertTrue(payload.contains("\"running\":true"));
  }

  @Test
  void newClientReceivesNoMatchWhenServiceReportsNone() throws Exception {
    when(matchControlService.currentMatch()).thenThrow(new MatchNotFoundException("No match"));

    StubWebSocketSession rawSession = new StubWebSocketSession("healthy");

    handler.afterConnectionEstablished(rawSession);

    assertTrue(
        rawSession.getSentMessages().getFirst().getPayload().contains("\"type\":\"NO_MATCH\""));
  }

  @Test
  void failedClientIsRemovedWithoutBlockingHealthyClients() throws Exception {
    when(matchControlService.currentMatch()).thenThrow(new MatchNotFoundException("No match"));
    StubWebSocketSession healthy = new StubWebSocketSession("healthy");
    FailingWebSocketSession failing = new FailingWebSocketSession("failing");

    handler.afterConnectionEstablished(healthy);
    handler.afterConnectionEstablished(failing);

    handler.broadcast(
        new MatchStreamMessage<>(MatchStreamMessageType.NO_MATCH, new NoMatchMessage()));

    assertEquals(2, healthy.getSentMessages().size());
    assertFalse(failing.isOpen());

    handler.broadcast(
        new MatchStreamMessage<>(MatchStreamMessageType.NO_MATCH, new NoMatchMessage()));

    assertEquals(3, healthy.getSentMessages().size());
  }

  @Test
  void closedSessionIsRemovedFromFutureBroadcasts() throws Exception {
    when(matchControlService.currentMatch()).thenThrow(new MatchNotFoundException("No match"));
    StubWebSocketSession session = new StubWebSocketSession("gone");

    handler.afterConnectionEstablished(session);
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    handler.broadcast(
        new MatchStreamMessage<>(MatchStreamMessageType.NO_MATCH, new NoMatchMessage()));

    assertEquals(1, session.getSentMessages().size());
  }

  private static class StubWebSocketSession implements WebSocketSession {

    private final String id;
    private final List<TextMessage> sentMessages = new ArrayList<>();
    private boolean open = true;
    private int textMessageSizeLimit;
    private int binaryMessageSizeLimit;

    private StubWebSocketSession(String id) {
      this.id = id;
    }

    List<TextMessage> getSentMessages() {
      return sentMessages;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public URI getUri() {
      return URI.create("ws://localhost/ws/match");
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
      return HttpHeaders.EMPTY;
    }

    @Override
    public Map<String, Object> getAttributes() {
      return Map.of();
    }

    @Override
    public Principal getPrincipal() {
      return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
      return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
      return null;
    }

    @Override
    public String getAcceptedProtocol() {
      return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
      this.textMessageSizeLimit = messageSizeLimit;
    }

    @Override
    public int getTextMessageSizeLimit() {
      return textMessageSizeLimit;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
      this.binaryMessageSizeLimit = messageSizeLimit;
    }

    @Override
    public int getBinaryMessageSizeLimit() {
      return binaryMessageSizeLimit;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
      return List.of();
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
      sentMessages.add((TextMessage) message);
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void close() throws IOException {
      open = false;
    }

    @Override
    public void close(CloseStatus status) throws IOException {
      open = false;
    }
  }

  private static final class FailingWebSocketSession extends StubWebSocketSession {

    private FailingWebSocketSession(String id) {
      super(id);
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
      throw new IOException("boom");
    }
  }
}
