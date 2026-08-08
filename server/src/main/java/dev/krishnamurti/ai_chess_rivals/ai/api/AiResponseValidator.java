package dev.krishnamurti.ai_chess_rivals.ai.api;

@FunctionalInterface
public interface AiResponseValidator {

  boolean isValid(String response);

  static AiResponseValidator nonBlank() {
    return response -> response != null && !response.isBlank();
  }
}
