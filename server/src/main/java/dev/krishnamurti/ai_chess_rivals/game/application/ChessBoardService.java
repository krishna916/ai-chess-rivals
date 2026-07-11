package dev.krishnamurti.ai_chess_rivals.game.application;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import java.util.Arrays;
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
    boolean capture = isCapture(board, move);
    boolean promotion = move.getPromotion() != Piece.NONE;
    board.doMove(move);
    return new AppliedMove(
        new BoardPosition(board.getFen()),
        capture,
        board.isKingAttacked(),
        board.isMated(),
        promotion);
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

  private boolean isCapture(Board board, Move move) {
    if (board.getPiece(move.getTo()) != Piece.NONE) {
      return true;
    }
    Piece movingPiece = board.getPiece(move.getFrom());
    return movingPiece.getPieceType() == PieceType.PAWN
        && move.getTo() == board.getEnPassant()
        && move.getFrom().getFile() != move.getTo().getFile();
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
