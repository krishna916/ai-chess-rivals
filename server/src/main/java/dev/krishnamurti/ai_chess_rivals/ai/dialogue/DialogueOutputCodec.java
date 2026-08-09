package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
final class DialogueOutputCodec {

  private static final List<String> FORBIDDEN_PHRASES =
      List.of(
          "kill you",
          "murder you",
          "hurt you",
          "suicide",
          "self-harm",
          "sexual",
          "nude",
          "worthless human");

  private final BeanOutputConverter<DialogueModelOutput> converter =
      new BeanOutputConverter<>(DialogueModelOutput.class);

  String format() {
    return converter.getFormat();
  }

  boolean isValid(String raw, DialogueReactionType expectedReactionType) {
    try {
      DialogueModelOutput output = parse(raw);
      return output.reactionType() == expectedReactionType && isSafe(output.text());
    } catch (RuntimeException ex) {
      return false;
    }
  }

  DialogueModelOutput parse(String raw) {
    DialogueModelOutput output = converter.convert(raw);
    if (output == null || output.text() == null || output.text().isBlank()) {
      throw new IllegalArgumentException("dialogue text must not be blank");
    }
    if (output.text().length() > 280) {
      throw new IllegalArgumentException("dialogue text must be at most 280 characters");
    }
    Objects.requireNonNull(output.emotion(), "dialogue emotion must not be null");
    Objects.requireNonNull(output.reactionType(), "dialogue reactionType must not be null");
    return output;
  }

  private static boolean isSafe(String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    return FORBIDDEN_PHRASES.stream().noneMatch(normalized::contains);
  }
}
