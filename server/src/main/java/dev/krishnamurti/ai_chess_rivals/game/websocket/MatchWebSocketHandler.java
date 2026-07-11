package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
final class MatchWebSocketHandler extends TextWebSocketHandler {

  @FunctionalInterface
  private interface MessageWriter {
    String write(MatchStreamMessage<?> message) throws JacksonException;
  }

  private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
  private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

  private final MessageWriter messageWriter;
  private final ObjectProvider<MatchControlService> matchControlServiceProvider;
  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final Object sessionLifecycleMonitor = new Object();

  public MatchWebSocketHandler(
      ObjectMapper objectMapper, ObjectProvider<MatchControlService> matchControlServiceProvider) {
    this.messageWriter = objectMapper::writeValueAsString;
    this.matchControlServiceProvider = matchControlServiceProvider;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    WebSocketSession concurrentSession =
        new ConcurrentWebSocketSessionDecorator(
            session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
    synchronized (sessionLifecycleMonitor) {
      sendInitialMessage(concurrentSession);
      if (concurrentSession.isOpen()) {
        sessions.put(session.getId(), concurrentSession);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
    log.debug("Match websocket client disconnected: {}", session.getId());
  }

  public void broadcast(MatchStreamMessage<?> message) {
    synchronized (sessionLifecycleMonitor) {
      if (sessions.isEmpty()) {
        return;
      }

      String payload;
      try {
        payload = messageWriter.write(message);
      } catch (JacksonException e) {
        log.error("Failed to serialize websocket message {}", message.type(), e);
        return;
      }

      for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
        sendSafely(entry.getKey(), entry.getValue(), payload);
      }
    }
  }

  private void sendInitialMessage(WebSocketSession session) {
    try {
      sendSafely(
          session.getId(),
          session,
          messageWriter.write(
              new MatchStreamMessage<>(
                  MatchStreamMessageType.MATCH_STATE,
                  MatchStateMessage.from(matchControlServiceProvider.getObject().currentMatch()))));
    } catch (MatchNotFoundException e) {
      try {
        sendSafely(
            session.getId(),
            session,
            messageWriter.write(
                new MatchStreamMessage<>(MatchStreamMessageType.NO_MATCH, new NoMatchMessage())));
      } catch (JacksonException serializationFailure) {
        log.error("Failed to serialize websocket message {}", MatchStreamMessageType.NO_MATCH, serializationFailure);
      }
    } catch (JacksonException e) {
      log.error("Failed to serialize websocket message {}", MatchStreamMessageType.MATCH_STATE, e);
    }
  }

  private void sendSafely(String sessionId, WebSocketSession session, String payload) {
    try {
      sendMessage(session, payload);
    } catch (IOException e) {
      sessions.remove(sessionId);
      closeQuietly(session);
      log.debug("Removed failed websocket client: {}", sessionId, e);
    }
  }

  private void sendMessage(WebSocketSession session, String payload) throws IOException {
    session.sendMessage(new TextMessage(payload));
  }

  private void closeQuietly(WebSocketSession session) {
    try {
      session.close();
    } catch (IOException e) {
      log.debug("Failed to close websocket session {}", session.getId(), e);
    }
  }
}
