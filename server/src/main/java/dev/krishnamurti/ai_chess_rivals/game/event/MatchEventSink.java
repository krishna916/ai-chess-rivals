package dev.krishnamurti.ai_chess_rivals.game.event;

import org.springframework.stereotype.Component;

@FunctionalInterface
public interface MatchEventSink {

  void publish(MatchEvent event);
}

@Component
final class NoOpMatchEventSink implements MatchEventSink {

  @Override
  public void publish(MatchEvent event) {}
}
