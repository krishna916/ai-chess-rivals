package dev.krishnamurti.ai_chess_rivals.ai.internal;

/** Internal seam that keeps provider failover tests independent of real network clients. */
@FunctionalInterface
interface ProviderChatClient {
  String complete(String prompt);
}
