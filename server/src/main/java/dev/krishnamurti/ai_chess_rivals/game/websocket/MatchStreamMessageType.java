package dev.krishnamurti.ai_chess_rivals.game.websocket;

public enum MatchStreamMessageType {
  MATCH_STATE,
  MATCH_STARTED,
  MOVE_PLAYED,
  MATCH_STOPPED,
  MATCH_FINISHED,
  NO_MATCH
}
