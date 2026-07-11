package dev.krishnamurti.ai_chess_rivals.game.web;

import static org.junit.jupiter.api.Assertions.*;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.domain.*;
import org.junit.jupiter.api.Test;

class MatchResponseMapperTest {

  @Test
  void mapsMatchSnapshotToMatchResponse() {
    Match match = Match.newGame();
    MatchSnapshot snapshot = new MatchSnapshot(match, true);

    MatchResponse response = MatchResponseMapper.map(snapshot);

    assertEquals(match.sideToMove(), response.sideToMove());
    assertEquals(match.currentPosition().fen(), response.fen());
    assertTrue(response.moves().isEmpty());
    assertEquals(match.status(), response.status());
    assertNull(response.result());
    assertTrue(response.running());
  }

  @Test
  void mapsMatchSnapshotWithMovesToMatchResponse() {
    Match match =
        Match.newGame()
            .recordMove(
                new MoveNotation("e2e4"),
                new BoardPosition(
                    "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"));
    MatchSnapshot snapshot = new MatchSnapshot(match, false);

    MatchResponse response = MatchResponseMapper.map(snapshot);

    assertEquals(match.sideToMove(), response.sideToMove());
    assertEquals(match.currentPosition().fen(), response.fen());
    assertEquals(1, response.moves().size());
    assertEquals(1, response.moves().get(0).sequenceNumber());
    assertEquals(PlayerColor.WHITE, response.moves().get(0).player());
    assertEquals("e2e4", response.moves().get(0).notation());
    assertEquals(match.currentPosition().fen(), response.moves().get(0).fenAfterMove());
    assertEquals(match.status(), response.status());
    assertNull(response.result());
    assertFalse(response.running());
  }
}
