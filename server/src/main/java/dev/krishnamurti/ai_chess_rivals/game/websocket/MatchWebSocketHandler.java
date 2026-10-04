package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
public final class MatchWebSocketHandler extends TextWebSocketHandler {

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
    withSessionTrace(
        session,
        () -> {
          WebSocketSession concurrentSession =
              new ConcurrentWebSocketSessionDecorator(
                  session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
          log.info("event=websocket.connected sessionId={}", session.getId());
          synchronized (sessionLifecycleMonitor) {
            sendInitialMessage(concurrentSession);
            if (concurrentSession.isOpen()) {
              sessions.put(session.getId(), concurrentSession);
              log.info("event=websocket.session_registered sessionId={}", session.getId());
            }
          }
        });
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    withSessionTrace(
        session,
        () -> {
          sessions.remove(session.getId());
          log.info(
              "event=websocket.disconnected sessionId={} closeCode={}",
              session.getId(),
              status.getCode());
        });
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    withSessionTrace(
        session,
        () -> {
          sessions.remove(session.getId());
          closeQuietly(session);
          log.info(
              "event=websocket.transport_error sessionId={} exceptionType={}",
              session.getId(),
              exception.getClass().getSimpleName());
        });
  }

  public void broadcast(MatchStreamMessage<?> message) {
    long startedAtNanos = System.nanoTime();
    synchronized (sessionLifecycleMonitor) {
      int attempted = 0;
      int succeeded = 0;
      int failed = 0;
      if (!sessions.isEmpty()) {
        String payload;
        try {
          payload = messageWriter.write(message);
        } catch (JacksonException e) {
          log.error("Failed to serialize websocket message {}", message.type(), e);
          log.info(
              "event=websocket.broadcast_completed eventType={} outcome=serialization_failed attempted=0 succeeded=0 failed=0 durationMs={}",
              message.type(),
              elapsedMillis(startedAtNanos));
          return;
        }

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
          attempted++;
          if (sendSafely(entry.getKey(), entry.getValue(), payload)) {
            succeeded++;
          } else {
            failed++;
          }
        }
      }
      log.info(
          "event=websocket.broadcast_completed eventType={} matchId={} attempted={} succeeded={} failed={} durationMs={}",
          message.type(),
          MDC.get("matchId"),
          attempted,
          succeeded,
          failed,
          elapsedMillis(startedAtNanos));
    }
  }

  private void sendInitialMessage(WebSocketSession session) {
    try {
      boolean delivered =
          sendSafely(
              session.getId(),
              session,
              messageWriter.write(
                  new MatchStreamMessage<>(
                      MatchStreamMessageType.MATCH_STATE,
                      MatchStateMessage.from(
                          matchControlServiceProvider.getObject().currentMatch()))));
      log.info(
          "event=websocket.initial_message sessionId={} outcome={} messageType={}",
          session.getId(),
          delivered ? "delivered" : "failed",
          MatchStreamMessageType.MATCH_STATE);
    } catch (MatchNotFoundException e) {
      try {
        boolean delivered =
            sendSafely(
                session.getId(),
                session,
                messageWriter.write(
                    new MatchStreamMessage<>(
                        MatchStreamMessageType.NO_MATCH, new NoMatchMessage())));
        log.info(
            "event=websocket.initial_message sessionId={} outcome={} messageType={}",
            session.getId(),
            delivered ? "delivered" : "failed",
            MatchStreamMessageType.NO_MATCH);
      } catch (JacksonException serializationFailure) {
        log.error(
            "Failed to serialize websocket message {}",
            MatchStreamMessageType.NO_MATCH,
            serializationFailure);
      }
    } catch (JacksonException e) {
      log.error("Failed to serialize websocket message {}", MatchStreamMessageType.MATCH_STATE, e);
    }
  }

  private boolean sendSafely(String sessionId, WebSocketSession session, String payload) {
    try {
      sendMessage(session, payload);
      return true;
    } catch (IOException | IllegalStateException e) {
      sessions.remove(sessionId);
      closeQuietly(session);
      log.info(
          "event=websocket.recipient_failed sessionId={} exceptionType={}",
          sessionId,
          e.getClass().getSimpleName());
      return false;
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

  private void withSessionTrace(WebSocketSession session, Runnable action) {
    Map<String, String> previousMdc = MDC.getCopyOfContextMap();
    Object requestId =
        session
            .getAttributes()
            .get(MatchWebSocketConfig.RequestTraceHandshakeInterceptor.TRACE_REQUEST_ID_ATTRIBUTE);
    MDC.put("requestId", requestId == null ? UUID.randomUUID().toString() : requestId.toString());
    MDC.put("sessionId", session.getId());
    try {
      action.run();
    } finally {
      if (previousMdc == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(new HashMap<>(previousMdc));
      }
    }
  }

  private static long elapsedMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000L;
  }
}
