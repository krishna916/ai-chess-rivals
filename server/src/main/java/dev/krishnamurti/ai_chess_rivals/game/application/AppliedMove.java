package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveDetails;
import java.util.Objects;

record AppliedMove(BoardPosition position, MoveDetails details) {

  AppliedMove {
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(details, "details must not be null");
  }

  boolean capture() {
    return details.capture();
  }

  boolean check() {
    return details.check();
  }

  boolean checkmate() {
    return details.checkmate();
  }

  boolean promotion() {
    return details.promotion();
  }
}
