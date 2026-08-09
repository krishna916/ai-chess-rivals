package dev.krishnamurti.ai_chess_rivals.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DialogueRequestTest {

  @Test
  void requestHistoryIsDefensivelyCopied() {
    List<DialogueHistoryLine> source = new ArrayList<>(List.of(historyLine(1)));
    DialogueStartRequest start = new DialogueStartRequest("blaze", "vesper", source);
    DialogueMoveRequest move =
        new DialogueMoveRequest(
            2, "blaze", "vesper", "e4", false, false, false, false, Optional.empty(), source);
    DialogueEndRequest end =
        new DialogueEndRequest(
            "blaze", DialogueOutcome.VICTORY, "vesper", DialogueOutcome.DEFEAT, 20, source);

    source.clear();

    assertThat(start.recentDialogue()).containsExactly(historyLine(1));
    assertThat(move.recentDialogue()).containsExactly(historyLine(1));
    assertThat(end.recentDialogue()).containsExactly(historyLine(1));
    assertThatThrownBy(() -> start.recentDialogue().add(historyLine(2)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void requestHistoryRejectsMoreThanFourLines() {
    List<DialogueHistoryLine> fiveLines =
        List.of(historyLine(1), historyLine(2), historyLine(3), historyLine(4), historyLine(5));

    assertThatThrownBy(() -> new DialogueStartRequest("blaze", "vesper", fiveLines))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DialogueMoveRequest(
                    2,
                    "blaze",
                    "vesper",
                    "e4",
                    false,
                    false,
                    false,
                    false,
                    Optional.empty(),
                    fiveLines))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DialogueEndRequest(
                    "blaze",
                    DialogueOutcome.VICTORY,
                    "vesper",
                    DialogueOutcome.DEFEAT,
                    20,
                    fiveLines))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DialogueHistoryLine historyLine(int ply) {
    return new DialogueHistoryLine(ply, "blaze", "Blaze", "A line at ply " + ply);
  }
}
