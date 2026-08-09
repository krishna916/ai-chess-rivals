package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueGenerator;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueStartRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
final class DialogueGenerationService implements DialogueGenerator {

  private final AiChatGateway aiChatGateway;
  private final PersonalityService personalityService;
  private final DialogueSpeakingPolicy speakingPolicy;
  private final DialoguePromptFactory promptFactory;
  private final DialogueOutputCodec outputCodec;
  private final DeterministicFallbackCatalog fallbackCatalog;

  DialogueGenerationService(
      AiChatGateway aiChatGateway,
      PersonalityService personalityService,
      DialogueSpeakingPolicy speakingPolicy,
      DialoguePromptFactory promptFactory,
      DialogueOutputCodec outputCodec,
      DeterministicFallbackCatalog fallbackCatalog) {
    this.aiChatGateway = Objects.requireNonNull(aiChatGateway, "aiChatGateway must not be null");
    this.personalityService =
        Objects.requireNonNull(personalityService, "personalityService must not be null");
    this.speakingPolicy = Objects.requireNonNull(speakingPolicy, "speakingPolicy must not be null");
    this.promptFactory = Objects.requireNonNull(promptFactory, "promptFactory must not be null");
    this.outputCodec = Objects.requireNonNull(outputCodec, "outputCodec must not be null");
    this.fallbackCatalog =
        Objects.requireNonNull(fallbackCatalog, "fallbackCatalog must not be null");
  }

  @Override
  public List<GeneratedDialogue> generateStart(DialogueStartRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    PersonalityPromptProfile white = profile(request.whitePersonalityKey());
    PersonalityPromptProfile black = profile(request.blackPersonalityKey());
    String format = outputCodec.format();
    return List.of(
        generateOne(
            white,
            promptFactory.startPrompt(request, white, black, format),
            DialogueReactionType.GAME_START,
            DialogueFallbackKind.START),
        generateOne(
            black,
            promptFactory.startPrompt(request, black, white, format),
            DialogueReactionType.GAME_START,
            DialogueFallbackKind.START));
  }

  @Override
  public Optional<GeneratedDialogue> generateMove(DialogueMoveRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    PersonalityPromptProfile mover = profile(request.moverPersonalityKey());
    PersonalityPromptProfile opponent = profile(request.opponentPersonalityKey());
    Optional<String> speakerKey = speakingPolicy.selectMoveSpeaker(request, mover, opponent);
    if (speakerKey.isEmpty()) {
      return Optional.empty();
    }
    PersonalityPromptProfile speaker =
        speakerKey.orElseThrow().equals(mover.key()) ? mover : opponent;
    PersonalityPromptProfile other = speaker.key().equals(mover.key()) ? opponent : mover;
    return Optional.of(
        generateOne(
            speaker,
            promptFactory.movePrompt(
                request, speaker, other, DialogueReactionType.MOVE_REACTION, outputCodec.format()),
            DialogueReactionType.MOVE_REACTION,
            DialogueFallbackKind.ORDINARY_REACTION));
  }

  @Override
  public List<GeneratedDialogue> generateEnd(DialogueEndRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    PersonalityPromptProfile white = profile(request.whitePersonalityKey());
    PersonalityPromptProfile black = profile(request.blackPersonalityKey());
    if (request.whiteOutcome() == DialogueOutcome.DRAW) {
      return List.of(
          generateEndLine(
              request,
              white,
              black,
              DialogueOutcome.DRAW,
              DialogueReactionType.DRAW,
              DialogueFallbackKind.FAILURE_RECOVERY),
          generateEndLine(
              request,
              black,
              white,
              DialogueOutcome.DRAW,
              DialogueReactionType.DRAW,
              DialogueFallbackKind.FAILURE_RECOVERY));
    }

    boolean whiteWon = request.whiteOutcome() == DialogueOutcome.VICTORY;
    PersonalityPromptProfile winner = whiteWon ? white : black;
    PersonalityPromptProfile loser = whiteWon ? black : white;
    return List.of(
        generateEndLine(
            request,
            winner,
            loser,
            DialogueOutcome.VICTORY,
            DialogueReactionType.VICTORY,
            DialogueFallbackKind.VICTORY),
        generateEndLine(
            request,
            loser,
            winner,
            DialogueOutcome.DEFEAT,
            DialogueReactionType.DEFEAT,
            DialogueFallbackKind.DEFEAT));
  }

  private GeneratedDialogue generateEndLine(
      DialogueEndRequest request,
      PersonalityPromptProfile speaker,
      PersonalityPromptProfile opponent,
      DialogueOutcome outcome,
      DialogueReactionType reactionType,
      DialogueFallbackKind fallbackKind) {
    return generateOne(
        speaker,
        promptFactory.endPrompt(
            request, speaker, opponent, outcome, reactionType, outputCodec.format()),
        reactionType,
        fallbackKind);
  }

  private GeneratedDialogue generateOne(
      PersonalityPromptProfile speaker,
      String prompt,
      DialogueReactionType expectedReactionType,
      DialogueFallbackKind fallbackKind) {
    String fallback = fallbackCatalog.fallbackFor(speaker.key(), fallbackKind);
    AiChatResult result =
        aiChatGateway.generate(
            new AiChatRequest(prompt, fallback),
            raw -> outputCodec.isValid(raw, expectedReactionType));
    if (result.source() == AiResponseSource.DETERMINISTIC_FALLBACK) {
      return new GeneratedDialogue(
          speaker.key(),
          result.content(),
          DialogueEmotion.NEUTRAL,
          expectedReactionType,
          result.source());
    }
    DialogueModelOutput output = outputCodec.parse(result.content());
    return new GeneratedDialogue(
        speaker.key(), output.text(), output.emotion(), output.reactionType(), result.source());
  }

  private PersonalityPromptProfile profile(String personalityKey) {
    return personalityService.requirePromptProfile(personalityKey);
  }
}
