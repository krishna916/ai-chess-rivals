package dev.krishnamurti.ai_chess_rivals.ai.api;

public interface AiChatGateway {

  AiChatResult generate(AiChatRequest request, AiResponseValidator validator);
}
