package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Objects;

public record AiChatResult(String content, AiResponseSource source) {

  public AiChatResult {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    Objects.requireNonNull(source, "source must not be null");
  }
}
