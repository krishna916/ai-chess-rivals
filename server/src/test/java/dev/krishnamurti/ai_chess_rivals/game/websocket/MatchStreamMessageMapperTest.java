package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.junit.jupiter.api.Assertions.*;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartBlockReason;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchFinished;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStopped;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import org.junit.jupiter.api.Test;

class MatchStreamMessageMapperTest {

  @Test
  void mapsMovePlayedEventToExplicitWebSocketPayload() {
    MovePlayed event =
        new MovePlayed(
            1,
            PlayerColor.WHITE,
            new MoveNotation("e2e4"),
            new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
            false,
            false,
            false,
            false);

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(event);

    assertEquals(MatchStreamMessageType.MOVE_PLAYED, message.type());
    assertInstanceOf(MovePlayedMessage.class, message.payload());
    MovePlayedMessage payload = (MovePlayedMessage) message.payload();
    assertEquals(1, payload.ply());
    assertEquals("e2e4", payload.notation());
    assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", payload.fen());
    assertFalse(payload.capture());
    assertFalse(payload.check());
    assertFalse(payload.checkmate());
    assertFalse(payload.promotion());
  }

  @Test
  void mapsMatchStartedEventToExplicitPayload() {
    MatchStarted event = new MatchStarted(PlayerColor.WHITE, BoardPosition.STARTING_POSITION);

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(event);

    assertEquals(MatchStreamMessageType.MATCH_STARTED, message.type());
    MatchStartedMessage payload = (MatchStartedMessage) message.payload();
    assertEquals(PlayerColor.WHITE, payload.sideToMove());
    assertEquals(BoardPosition.STARTING_POSITION.fen(), payload.fen());
  }

  @Test
  void mapsMatchFinishedEventToExplicitPayload() {
    MatchFinished event =
        new MatchFinished(
            GameResult.WHITE_WINS, new BoardPosition("7k/5Q2/7K/8/8/8/8/8 b - - 0 1"), 73);

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(event);

    assertEquals(MatchStreamMessageType.MATCH_FINISHED, message.type());
    MatchFinishedMessage payload = (MatchFinishedMessage) message.payload();
    assertEquals(GameResult.WHITE_WINS, payload.result());
    assertEquals("7k/5Q2/7K/8/8/8/8/8 b - - 0 1", payload.fen());
    assertEquals(73, payload.totalPlies());
  }

  @Test
  void mapsMatchStoppedEventToAuthoritativePayload() {
    MatchStopped event = new MatchStopped(PlayerColor.BLACK, new BoardPosition("after-e4"), 1);

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(event);

    assertEquals(MatchStreamMessageType.MATCH_STOPPED, message.type());
    MatchStoppedMessage payload = (MatchStoppedMessage) message.payload();
    assertEquals(PlayerColor.BLACK, payload.sideToMove());
    assertEquals("after-e4", payload.fen());
    assertEquals(1, payload.totalPlies());
  }

  @Test
  void createsMatchStatePayloadFromSnapshot() {
    Match match = Match.newGame();
    MatchStartAvailability availability =
        new MatchStartAvailability(false, MatchStartBlockReason.MATCH_ALREADY_RUNNING, 0, 2, 12);

    MatchStateMessage payload =
        MatchStateMessage.from(new MatchSnapshot(match, true, availability));

    assertEquals(PlayerColor.WHITE, payload.sideToMove());
    assertEquals(match.currentPosition().fen(), payload.fen());
    assertTrue(payload.moves().isEmpty());
    assertEquals(GameStatus.IN_PROGRESS, payload.status());
    assertNull(payload.result());
    assertTrue(payload.running());
    assertSame(availability, payload.startAvailability());
  }
}
