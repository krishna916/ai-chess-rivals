package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.junit.jupiter.api.Assertions.*;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.game.TestMatchFixtures;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartBlockReason;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveDetails;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.DialoguePlayed;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchFinished;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStopped;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import dev.krishnamurti.ai_chess_rivals.game.web.DialogueResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
                false),
            Optional.empty());

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(event);

    assertEquals(MatchStreamMessageType.MOVE_PLAYED, message.type());
    assertInstanceOf(MovePlayedMessage.class, message.payload());
    MovePlayedMessage payload = (MovePlayedMessage) message.payload();
    assertEquals(1, payload.ply());
    assertEquals("e2e4", payload.notation());
    assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", payload.fen());
    assertEquals(ChessPieceType.PAWN, payload.movingPiece());
    assertEquals(PlayerColor.WHITE, payload.movingPieceColor());
    assertEquals("e2", payload.sourceSquare());
    assertEquals("e4", payload.destinationSquare());
    assertEquals(ChessPieceType.ROOK, payload.capturedPiece());
    assertEquals(PlayerColor.BLACK, payload.capturedPieceColor());
    assertEquals(ChessPieceType.QUEEN, payload.promotedPiece());
    assertEquals(CastlingSide.KING_SIDE, payload.castlingSide());
    assertTrue(payload.capture());
    assertFalse(payload.check());
    assertFalse(payload.checkmate());
    assertTrue(payload.promotion());
  }

  @Test
  void mapsMatchStartedEventToExplicitPayload() {
    UUID matchId = UUID.randomUUID();
    MatchStarted event =
        new MatchStarted(
            matchId,
            PlayerColor.WHITE,
            BoardPosition.STARTING_POSITION,
            TestMatchFixtures.TEST_RIVALRY);

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(event);

    assertEquals(MatchStreamMessageType.MATCH_STARTED, message.type());
    MatchStartedMessage payload = (MatchStartedMessage) message.payload();
    assertEquals(matchId, payload.matchId());
    assertEquals(
        new dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse(
            "white-test", "White Test"),
        payload.whitePersonality());
    assertEquals(
        new dev.krishnamurti.ai_chess_rivals.game.web.MatchPersonalityResponse(
            "black-test", "Black Test"),
        payload.blackPersonality());
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
    Match match = TestMatchFixtures.newMatch();
    MatchStartAvailability availability =
        new MatchStartAvailability(false, MatchStartBlockReason.MATCH_ALREADY_RUNNING, 0, 2, 12);

    PersistedDialogue line = dialogue(match.id(), 1, 1, "hello");
    MatchStateMessage payload =
        MatchStateMessage.from(new MatchSnapshot(match, true, availability, List.of(line)));

    assertEquals(match.id(), payload.matchId());
    assertEquals(PlayerColor.WHITE, payload.sideToMove());
    assertEquals(match.currentPosition().fen(), payload.fen());
    assertTrue(payload.moves().isEmpty());
    assertEquals(GameStatus.IN_PROGRESS, payload.status());
    assertNull(payload.result());
    assertTrue(payload.running());
    assertSame(availability, payload.startAvailability());
    assertEquals(List.of(1L), payload.dialogue().stream().map(DialogueResponse::id).toList());
  }

  @Test
  void mapsPersistedDialogueToLiveMessage() {
    PersistedDialogue line = dialogue(UUID.randomUUID(), 7, 3, "stored line");

    MatchStreamMessage<?> message = new MatchStreamMessageMapper().map(new DialoguePlayed(line));

    assertEquals(MatchStreamMessageType.DIALOGUE_PLAYED, message.type());
    DialoguePlayedMessage payload = (DialoguePlayedMessage) message.payload();
    assertEquals(line.id(), payload.id());
    assertEquals(line.matchId(), payload.matchId());
    assertEquals(line.triggerType(), payload.triggerType());
    assertEquals(line.triggerPly(), payload.triggerPly());
    assertEquals(line.personalityKey(), payload.personalityKey());
    assertEquals(line.personalityDisplayName(), payload.personalityDisplayName());
    assertEquals(line.text(), payload.text());
    assertEquals(line.emotion(), payload.emotion());
    assertEquals(line.reactionType(), payload.reactionType());
    assertEquals(line.source(), payload.source());
    assertEquals(line.createdAt(), payload.createdAt());
  }

  private static PersistedDialogue dialogue(UUID matchId, long id, int ply, String text) {
    return new PersistedDialogue(
        id,
        matchId,
        DialogueTriggerType.MOVE,
        ply,
        "blaze",
        "Blaze",
        text,
        DialogueEmotion.CONFIDENT,
        DialogueReactionType.MOVE_REACTION,
        AiResponseSource.DETERMINISTIC_FALLBACK,
        Instant.parse("2026-08-16T00:00:00Z").plusSeconds(id));
  }
}
