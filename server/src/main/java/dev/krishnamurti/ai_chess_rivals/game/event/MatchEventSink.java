package dev.krishnamurti.ai_chess_rivals.game.event;

@FunctionalInterface
public interface MatchEventSink {

  void publish(MatchEvent event);
}
