package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.event.MatchEvent;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchFinished;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStopped;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import org.springframework.stereotype.Component;

@Component
final class MatchStreamMessageMapper {

  public MatchStreamMessage<?> map(MatchEvent event) {
    return switch (event) {
      case MatchStarted started ->
          new MatchStreamMessage<>(
              MatchStreamMessageType.MATCH_STARTED,
              new MatchStartedMessage(started.sideToMove(), started.position().fen()));
      case MovePlayed move ->
          new MatchStreamMessage<>(
              MatchStreamMessageType.MOVE_PLAYED,
              new MovePlayedMessage(
                  move.ply(),
                  move.player(),
                  move.notation().value(),
                  move.position().fen(),
                  move.capture(),
                  move.check(),
                  move.checkmate(),
                  move.promotion()));
      case MatchStopped stopped ->
          new MatchStreamMessage<>(
              MatchStreamMessageType.MATCH_STOPPED,
              new MatchStoppedMessage(
                  stopped.sideToMove(), stopped.position().fen(), stopped.totalPlies()));
      case MatchFinished finished ->
          new MatchStreamMessage<>(
              MatchStreamMessageType.MATCH_FINISHED,
              new MatchFinishedMessage(
                  finished.result(), finished.finalPosition().fen(), finished.totalPlies()));
    };
  }
}
