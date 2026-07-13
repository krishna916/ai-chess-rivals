package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;

public record MovePlayedMessage(
    int ply,
    PlayerColor player,
    String notation,
    String fen,
    ChessPieceType movingPiece,
    PlayerColor movingPieceColor,
    String sourceSquare,
    String destinationSquare,
    ChessPieceType capturedPiece,
    PlayerColor capturedPieceColor,
    ChessPieceType promotedPiece,
    CastlingSide castlingSide,
    boolean capture,
    boolean check,
    boolean checkmate,
    boolean promotion) {}
