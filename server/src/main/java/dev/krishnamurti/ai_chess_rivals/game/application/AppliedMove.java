package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import java.util.Objects;

record AppliedMove(
    BoardPosition position, boolean capture, boolean check, boolean checkmate, boolean promotion) {

  AppliedMove {
    Objects.requireNonNull(position, "position must not be null");
  }
}
