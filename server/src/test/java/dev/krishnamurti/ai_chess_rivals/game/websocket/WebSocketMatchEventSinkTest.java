package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import org.junit.jupiter.api.Test;

class WebSocketMatchEventSinkTest {

  @Test
  void sinkDoesNotPropagateTransportFailures() {
    MatchStreamMessageMapper mapper = mock(MatchStreamMessageMapper.class);
    MatchWebSocketHandler handler = mock(MatchWebSocketHandler.class);
    MatchStarted event = new MatchStarted(PlayerColor.WHITE, BoardPosition.STARTING_POSITION);
    MatchStreamMessage<MatchStartedMessage> message =
        new MatchStreamMessage<>(
            MatchStreamMessageType.MATCH_STARTED,
            new MatchStartedMessage(PlayerColor.WHITE, BoardPosition.STARTING_POSITION.fen()));

    doReturn(message).when(mapper).map(event);
    doThrow(new RuntimeException("boom")).when(handler).broadcast(message);

    WebSocketMatchEventSink sink = new WebSocketMatchEventSink(mapper, handler);

    assertDoesNotThrow(() -> sink.publish(event));
    verify(mapper).map(event);
    verify(handler).broadcast(message);
  }
}
