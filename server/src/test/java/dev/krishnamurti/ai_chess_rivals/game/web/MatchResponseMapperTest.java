package dev.krishnamurti.ai_chess_rivals.game.web;

import static org.junit.jupiter.api.Assertions.*;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartBlockReason;
import dev.krishnamurti.ai_chess_rivals.game.domain.*;
import org.junit.jupiter.api.Test;

class MatchResponseMapperTest {

  @Test
  void mapsMatchSnapshotToMatchResponse() {
    Match match = Match.newGame();
    MatchStartAvailability availability =
        new MatchStartAvailability(false, MatchStartBlockReason.MATCH_ALREADY_RUNNING, 0, 1, 12);
    MatchSnapshot snapshot = new MatchSnapshot(match, true, availability);

    MatchResponse response = MatchResponseMapper.map(snapshot);

    assertEquals(match.sideToMove(), response.sideToMove());
    assertEquals(match.currentPosition().fen(), response.fen());
    assertTrue(response.moves().isEmpty());
    assertEquals(match.status(), response.status());
    assertNull(response.result());
    assertTrue(response.running());
    assertSame(availability, response.startAvailability());
  }

  @Test
  void mapsMatchSnapshotWithMovesToMatchResponse() {
    Match match =
        Match.newGame()
            .recordMove(
                new MoveNotation("e2e4"),
                new BoardPosition("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
                new MoveDetails(
                    ChessPieceType.PAWN,
                    PlayerColor.WHITE,
                    "e2",
                    "e4",
                    ChessPieceType.ROOK,
                    PlayerColor.BLACK,
                    ChessPieceType.QUEEN,
                    CastlingSide.KING_SIDE,
                    false,
                    false));
    MatchSnapshot snapshot = new MatchSnapshot(match, false);

    MatchResponse response = MatchResponseMapper.map(snapshot);

    assertEquals(match.sideToMove(), response.sideToMove());
    assertEquals(match.currentPosition().fen(), response.fen());
    assertEquals(1, response.moves().size());
    assertEquals(1, response.moves().get(0).sequenceNumber());
    assertEquals(PlayerColor.WHITE, response.moves().get(0).player());
    assertEquals("e2e4", response.moves().get(0).notation());
    assertEquals(match.currentPosition().fen(), response.moves().get(0).fenAfterMove());
    assertEquals(ChessPieceType.PAWN, response.moves().get(0).movingPiece());
    assertEquals(PlayerColor.WHITE, response.moves().get(0).movingPieceColor());
    assertEquals("e2", response.moves().get(0).sourceSquare());
    assertEquals("e4", response.moves().get(0).destinationSquare());
    assertEquals(ChessPieceType.ROOK, response.moves().get(0).capturedPiece());
    assertEquals(PlayerColor.BLACK, response.moves().get(0).capturedPieceColor());
    assertEquals(ChessPieceType.QUEEN, response.moves().get(0).promotedPiece());
    assertEquals(CastlingSide.KING_SIDE, response.moves().get(0).castlingSide());
    assertTrue(response.moves().get(0).capture());
    assertTrue(response.moves().get(0).promotion());
    assertEquals(match.status(), response.status());
    assertNull(response.result());
    assertFalse(response.running());
  }
}
