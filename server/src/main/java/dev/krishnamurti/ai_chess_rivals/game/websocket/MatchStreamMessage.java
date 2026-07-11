package dev.krishnamurti.ai_chess_rivals.game.websocket;

import java.util.Objects;

public record MatchStreamMessage<T>(MatchStreamMessageType type, T payload) {

  public MatchStreamMessage {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(payload, "payload must not be null");
  }
}
