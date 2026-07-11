package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.event.MatchEvent;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public final class WebSocketMatchEventSink implements MatchEventSink {

  private final MatchStreamMessageMapper mapper;
  private final MatchWebSocketHandler handler;

  public WebSocketMatchEventSink(MatchStreamMessageMapper mapper, MatchWebSocketHandler handler) {
    this.mapper = mapper;
    this.handler = handler;
  }

  @Override
  public void publish(MatchEvent event) {
    try {
      handler.broadcast(mapper.map(event));
    } catch (Exception e) {
      log.error("Failed to deliver websocket match event {}", event.getClass().getSimpleName(), e);
    }
  }
}
