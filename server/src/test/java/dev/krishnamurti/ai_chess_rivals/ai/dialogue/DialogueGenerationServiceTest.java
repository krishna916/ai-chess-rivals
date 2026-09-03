package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatResult;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueStartRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityService;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DialogueGenerationServiceTest {

  private final AiChatGateway gateway = mock(AiChatGateway.class);
  private final PersonalityService personalityService = mock(PersonalityService.class);
  private final DialoguePromptFactory promptFactory = new DialoguePromptFactory();
  private final DialogueOutputCodec outputCodec = new DialogueOutputCodec();
  private final DeterministicFallbackCatalog fallbackCatalog = new DeterministicFallbackCatalog();
  private final PersonalityPromptProfile blaze = profile("blaze", "Blaze", "0.820");
  private final PersonalityPromptProfile vesper = profile("vesper", "Vesper", "0.360");
  private final List<AiChatRequest> requests = new ArrayList<>();

  @BeforeEach
  void setUpProfiles() {
    when(personalityService.requirePromptProfile("blaze")).thenReturn(blaze);
    when(personalityService.requirePromptProfile("vesper")).thenReturn(vesper);
  }

  @Test
  void generatesStartInWhiteThenBlackOrder() {
    gatewayReturnsValidProviderOutput();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));

    List<GeneratedDialogue> result =
        service.generateStart(new DialogueStartRequest("blaze", "vesper", List.of()));

    assertThat(result)
        .extracting(GeneratedDialogue::personalityKey)
        .containsExactly("blaze", "vesper");
    assertThat(requests).hasSize(2);
  }

  @Test
  void suppressedOrdinaryMoveDoesNotCallGateway() {
    gatewayReturnsValidProviderOutput();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.999));
    DialogueMoveRequest request =
        new DialogueMoveRequest(
            1, "blaze", "vesper", "e4", false, false, false, false, Optional.empty(), List.of());

    assertThat(service.generateMove(request)).isEmpty();
    assertThat(requests).isEmpty();
  }

  @Test
  void majorMistakeMakesOpponentSpeak() {
    gatewayReturnsValidProviderOutput();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.999));
    DialogueMoveRequest request =
        new DialogueMoveRequest(
            1,
            "blaze",
            "vesper",
            "Qxd5",
            true,
            false,
            false,
            false,
            Optional.of(
                new EvaluationSwing(300, -100, -400, EvaluationSwingClassification.MAJOR_MISTAKE)),
            List.of());

    assertThat(service.generateMove(request))
        .get()
        .extracting(GeneratedDialogue::personalityKey)
        .isEqualTo("vesper");
    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().prompt())
        .contains("Move owner: Blaze")
        .contains("Speaker role: OPPONENT (the other personality played this move)")
        .contains("classification=MAJOR_MISTAKE")
        .contains("from Blaze's perspective");
  }

  @Test
  void checkMakesOpponentSpeakWithoutClaimingTheOpponentDeliveredCheck() {
    gatewayReturnsValidProviderOutput();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));
    DialogueMoveRequest request =
        new DialogueMoveRequest(
            5, "blaze", "vesper", "Qh5+", false, true, false, false, Optional.empty(), List.of());

    GeneratedDialogue result = service.generateMove(request).orElseThrow();

    assertThat(result.personalityKey()).isEqualTo("vesper");
    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().prompt())
        .contains("Move owner: Blaze")
        .contains("Speaker role: OPPONENT (the other personality played this move)")
        .contains("check=true means the mover gave check to the opponent");
  }

  @Test
  void mapsProviderOutputAndSource() {
    gatewayReturnsValidProviderOutput();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));
    DialogueMoveRequest request =
        new DialogueMoveRequest(
            1, "blaze", "vesper", "e4", false, false, false, false, Optional.empty(), List.of());

    GeneratedDialogue result = service.generateMove(request).orElseThrow();

    assertThat(result.text()).isEqualTo("Efficient—for me.");
    assertThat(result.emotion()).isEqualTo(DialogueEmotion.CALM);
    assertThat(result.reactionType()).isEqualTo(DialogueReactionType.MOVE_REACTION);
    assertThat(result.source()).isEqualTo(AiResponseSource.REMOTE_PRIMARY);
    assertThat(requests).hasSize(1);
    assertThat(requests.getFirst().prompt())
        .contains("Move owner: Blaze")
        .contains("Speaker role: MOVER (you played this move)");
  }

  @Test
  void mapsDeterministicFallbackWithoutParsingItsText() {
    gatewayReturnsFallback();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));

    GeneratedDialogue result =
        service.generateStart(new DialogueStartRequest("blaze", "vesper", List.of())).getFirst();

    assertThat(result.text())
        .isEqualTo(fallbackCatalog.fallbackFor("blaze", DialogueFallbackKind.START));
    assertThat(result.emotion()).isEqualTo(DialogueEmotion.NEUTRAL);
    assertThat(result.reactionType()).isEqualTo(DialogueReactionType.GAME_START);
    assertThat(result.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
  }

  @Test
  void generatesDecisiveEndWinnerThenLoserAndDrawWhiteThenBlack() {
    gatewayReturnsFallback();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));

    List<GeneratedDialogue> decisive =
        service.generateEnd(
            new DialogueEndRequest(
                "blaze", DialogueOutcome.VICTORY, "vesper", DialogueOutcome.DEFEAT, 20, List.of()));
    List<GeneratedDialogue> draw =
        service.generateEnd(
            new DialogueEndRequest(
                "blaze", DialogueOutcome.DRAW, "vesper", DialogueOutcome.DRAW, 20, List.of()));

    assertThat(decisive)
        .extracting(GeneratedDialogue::personalityKey)
        .containsExactly("blaze", "vesper");
    assertThat(decisive)
        .extracting(GeneratedDialogue::reactionType)
        .containsExactly(DialogueReactionType.VICTORY, DialogueReactionType.DEFEAT);
    assertThat(draw)
        .extracting(GeneratedDialogue::personalityKey)
        .containsExactly("blaze", "vesper");
    assertThat(draw)
        .allSatisfy(line -> assertThat(line.reactionType()).isEqualTo(DialogueReactionType.DRAW));
    assertThat(draw.getFirst().text())
        .isEqualTo(fallbackCatalog.fallbackFor("blaze", DialogueFallbackKind.FAILURE_RECOVERY));
  }

  @Test
  void passesRecentHistoryIntoRenderedPrompt() {
    gatewayReturnsValidProviderOutput();
    DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));
    DialogueMoveRequest request =
        new DialogueMoveRequest(
            5,
            "blaze",
            "vesper",
            "Qxd5+",
            true,
            true,
            false,
            false,
            Optional.empty(),
            List.of(
                new dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine(
                    4, "vesper", "Vesper", "Noted.")));

    service.generateMove(request);

    assertThat(requests.getFirst().prompt()).contains("Noted.");
  }

  private DialogueGenerationService service(DialogueSpeakingPolicy policy) {
    return new DialogueGenerationService(
        gateway, personalityService, policy, promptFactory, outputCodec, fallbackCatalog);
  }

  private void gatewayReturnsValidProviderOutput() {
    when(gateway.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              AiChatRequest request = invocation.getArgument(0);
              requests.add(request);
              return new AiChatResult(
                  "{\"text\":\"Efficient—for me.\",\"emotion\":\"CALM\",\"reactionType\":\""
                      + reactionFor(request.prompt())
                      + "\"}",
                  AiResponseSource.REMOTE_PRIMARY);
            });
  }

  private void gatewayReturnsFallback() {
    when(gateway.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              AiChatRequest request = invocation.getArgument(0);
              requests.add(request);
              return new AiChatResult(
                  request.deterministicFallback(), AiResponseSource.DETERMINISTIC_FALLBACK);
            });
  }

  private static String reactionFor(String prompt) {
    if (prompt.contains("Return reactionType exactly as GAME_START")) return "GAME_START";
    if (prompt.contains("Return reactionType exactly as VICTORY")) return "VICTORY";
    if (prompt.contains("Return reactionType exactly as DEFEAT")) return "DEFEAT";
    if (prompt.contains("Return reactionType exactly as DRAW")) return "DRAW";
    return "MOVE_REACTION";
  }

  private static PersonalityPromptProfile profile(String key, String name, String probability) {
    return new PersonalityPromptProfile(
        key,
        name,
        "Competitive traits",
        new BigDecimal(probability),
        "Dry style",
        "PG-13 boundary");
  }
}
