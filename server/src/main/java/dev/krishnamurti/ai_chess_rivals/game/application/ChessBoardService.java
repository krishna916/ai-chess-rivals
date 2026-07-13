package dev.krishnamurti.ai_chess_rivals.game.application;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveDetails;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Isolates chesslib-based board updates and terminal-state detection from the match engine. */
@Service
public class ChessBoardService {

  public AppliedMove applyMove(BoardPosition currentPosition, MoveNotation moveNotation) {
    Board board = loadBoard(currentPosition);
    Move move = parseMove(moveNotation, board.getSideToMove());
    if (!board.legalMoves().contains(move)) {
      throw new IllegalArgumentException(
          "Illegal move for current position: " + moveNotation.value());
    }
    Piece movingPiece = board.getPiece(move.getFrom());
    Piece capturedPiece = capturedPiece(board, move, movingPiece);
    ChessPieceType promotedPiece =
        move.getPromotion() == Piece.NONE ? null : pieceType(move.getPromotion());
    CastlingSide castlingSide = castlingSide(move, movingPiece);
    board.doMove(move);
    return new AppliedMove(
        new BoardPosition(board.getFen()),
        new MoveDetails(
            pieceType(movingPiece),
            playerColor(movingPiece),
            move.getFrom().value().toLowerCase(Locale.ROOT),
            move.getTo().value().toLowerCase(Locale.ROOT),
            capturedPiece == Piece.NONE ? null : pieceType(capturedPiece),
            capturedPiece == Piece.NONE ? null : playerColor(capturedPiece),
            promotedPiece,
            castlingSide,
            board.isKingAttacked(),
            board.isMated()));
  }

  public Optional<GameResult> determineResult(
      BoardPosition currentPosition, PlayerColor sideToMove, int currentPositionOccurrences) {
    Board board = loadBoard(currentPosition);
    verifySideToMove(board, sideToMove);

    if (board.isMated()) {
      return Optional.of(
          sideToMove == PlayerColor.WHITE ? GameResult.BLACK_WINS : GameResult.WHITE_WINS);
    }
    if (board.isStaleMate()
        || currentPositionOccurrences >= 3
        || board.isInsufficientMaterial()
        || board.getHalfMoveCounter() >= 100) {
      return Optional.of(GameResult.DRAW);
    }
    return Optional.empty();
  }

  public boolean isFinished(
      BoardPosition currentPosition, PlayerColor sideToMove, int currentPositionOccurrences) {
    return determineResult(currentPosition, sideToMove, currentPositionOccurrences).isPresent();
  }

  public String normalizedPositionKey(BoardPosition position) {
    Board board = loadBoard(position);
    String[] fenSegments = board.getFen().split(" ");
    if (fenSegments.length < 4) {
      throw new IllegalArgumentException("Invalid board position: " + position.fen());
    }
    return String.join(" ", Arrays.copyOf(fenSegments, 4));
  }

  private Board loadBoard(BoardPosition position) {
    try {
      Board board = new Board();
      board.loadFromFen(position.fen());
      return board;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid board position: " + position.fen(), e);
    }
  }

  private Move parseMove(MoveNotation notation, Side sideToMove) {
    try {
      return new Move(notation.value(), sideToMove);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid move notation: " + notation.value(), e);
    }
  }

  private Piece capturedPiece(Board board, Move move, Piece movingPiece) {
    Piece destinationPiece = board.getPiece(move.getTo());
    if (destinationPiece != Piece.NONE) {
      return destinationPiece;
    }
    if (movingPiece.getPieceType() == PieceType.PAWN
        && move.getTo() == board.getEnPassant()
        && move.getFrom().getFile() != move.getTo().getFile()) {
      return board.getPiece(Square.encode(move.getFrom().getRank(), move.getTo().getFile()));
    }
    return Piece.NONE;
  }

  private CastlingSide castlingSide(Move move, Piece movingPiece) {
    if (movingPiece.getPieceType() != PieceType.KING) {
      return null;
    }
    String notation = (move.getFrom().value() + move.getTo().value()).toLowerCase(Locale.ROOT);
    return switch (notation) {
      case "e1g1", "e8g8" -> CastlingSide.KING_SIDE;
      case "e1c1", "e8c8" -> CastlingSide.QUEEN_SIDE;
      default -> null;
    };
  }

  private ChessPieceType pieceType(Piece piece) {
    return switch (piece.getPieceType()) {
      case PAWN -> ChessPieceType.PAWN;
      case KNIGHT -> ChessPieceType.KNIGHT;
      case BISHOP -> ChessPieceType.BISHOP;
      case ROOK -> ChessPieceType.ROOK;
      case QUEEN -> ChessPieceType.QUEEN;
      case KING -> ChessPieceType.KING;
      case NONE -> throw new IllegalArgumentException("Piece must not be NONE");
    };
  }

  private PlayerColor playerColor(Piece piece) {
    return piece.getPieceSide() == Side.WHITE ? PlayerColor.WHITE : PlayerColor.BLACK;
  }

  private void verifySideToMove(Board board, PlayerColor expectedSideToMove) {
    PlayerColor actualSideToMove =
        board.getSideToMove() == Side.WHITE ? PlayerColor.WHITE : PlayerColor.BLACK;
    if (actualSideToMove != expectedSideToMove) {
      throw new IllegalArgumentException(
          "Board side to move does not match expected side: expected "
              + expectedSideToMove
              + " but was "
              + actualSideToMove);
    }
  }
}
