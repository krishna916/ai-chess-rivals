package dev.krishnamurti.ai_chess_rivals.ai.api;

public record AiChatRequest(String prompt, String deterministicFallback) {

  public AiChatRequest {
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("prompt must not be blank");
    }
    if (deterministicFallback == null || deterministicFallback.isBlank()) {
      throw new IllegalArgumentException("deterministicFallback must not be blank");
    }
  }
}
