package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueStartRequest;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

@Component
final class DialoguePromptFactory {

  private static final String RULES =
      """
      Rules:
      - Generate fictional PG-13 chess-rivalry dialogue only.
      - Never calculate, choose, validate, or recommend a chess move.
      - Never use consumer-engine labels such as "brilliant move".
      - Keep the dialogue concise: normally one sentence, at most two short sentences.
      - Recent dialogue is optional context. Reply to it only when the latest relevant line naturally connects to the current event; otherwise react to the current event without forcing a comeback.
      - Do not use slurs, sexual content, threats, self-harm language, hate, personally targeted abuse, or encouragement of real violence.
      - Return reactionType exactly as {reactionType}.

      {format}
      """;

  private static final String START_TEMPLATE =
      """
      You are {speakerDisplayName}, opening a fictional AI-vs-AI chess match against {opponentDisplayName}.
      Personality traits: {speakerTraits}
      Style guidance: {speakerStyle}
      Boundary guidance: {speakerBoundary}
      Give the opening line for the match. Recent dialogue: {recentDialogue}
      """
          + RULES;

  private static final String MOVE_TEMPLATE =
      """
      You are {speakerDisplayName}, reacting to a committed chess event against {opponentDisplayName}.
      Personality traits: {speakerTraits}
      Style guidance: {speakerStyle}
      Boundary guidance: {speakerBoundary}
      Ply: {ply}
      Move: {moveNotation}
      Facts: capture={capture}, check={check}, checkmate={checkmate}, promotion={promotion}
      {evaluation}
      Recent dialogue: {recentDialogue}
      React to the event in character.
      """
          + RULES;

  private static final String END_TEMPLATE =
      """
      You are {speakerDisplayName}, speaking as the {outcomeRole} of a fictional chess match against {opponentDisplayName}.
      Personality traits: {speakerTraits}
      Style guidance: {speakerStyle}
      Boundary guidance: {speakerBoundary}
      The match ended after {totalPlies} plies. Recent dialogue: {recentDialogue}
      Give one concise result reaction.
      """
          + RULES;

  private static final String DRAW_TEMPLATE =
      """
      You are {speakerDisplayName}, reacting to a drawn fictional chess match against {opponentDisplayName}.
      Personality traits: {speakerTraits}
      Style guidance: {speakerStyle}
      Boundary guidance: {speakerBoundary}
      The match ended after {totalPlies} plies. Recent dialogue: {recentDialogue}
      Give one concise draw reaction.
      """
          + RULES;

  String startPrompt(
      DialogueStartRequest request,
      PersonalityPromptProfile speaker,
      PersonalityPromptProfile opponent,
      String format) {
    Map<String, Object> variables =
        baseVariables(speaker, opponent, DialogueReactionType.GAME_START, format);
    variables.put("recentDialogue", history(request.recentDialogue()));
    return render(START_TEMPLATE, variables);
  }

  String movePrompt(
      DialogueMoveRequest request,
      PersonalityPromptProfile speaker,
      PersonalityPromptProfile opponent,
      DialogueReactionType reactionType,
      String format) {
    Map<String, Object> variables = baseVariables(speaker, opponent, reactionType, format);
    variables.put("ply", request.ply());
    variables.put("moveNotation", request.moveNotation());
    variables.put("capture", request.capture());
    variables.put("check", request.check());
    variables.put("checkmate", request.checkmate());
    variables.put("promotion", request.promotion());
    variables.put("evaluation", evaluation(request.evaluation()));
    variables.put("recentDialogue", history(request.recentDialogue()));
    return render(MOVE_TEMPLATE, variables);
  }

  String endPrompt(
      DialogueEndRequest request,
      PersonalityPromptProfile speaker,
      PersonalityPromptProfile opponent,
      DialogueOutcome outcome,
      DialogueReactionType reactionType,
      String format) {
    Map<String, Object> variables = baseVariables(speaker, opponent, reactionType, format);
    variables.put("outcomeRole", outcome == DialogueOutcome.VICTORY ? "winner" : "loser");
    variables.put("totalPlies", request.totalPlies());
    variables.put("recentDialogue", history(request.recentDialogue()));
    String template = outcome == DialogueOutcome.DRAW ? DRAW_TEMPLATE : END_TEMPLATE;
    return render(template, variables);
  }

  private static Map<String, Object> baseVariables(
      PersonalityPromptProfile speaker,
      PersonalityPromptProfile opponent,
      DialogueReactionType reactionType,
      String format) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("speakerDisplayName", speaker.displayName());
    variables.put("speakerTraits", speaker.promptTraits());
    variables.put("speakerStyle", speaker.styleGuidance());
    variables.put("speakerBoundary", speaker.boundaryGuidance());
    variables.put("opponentDisplayName", opponent.displayName());
    variables.put("reactionType", reactionType);
    variables.put("format", format);
    return variables;
  }

  private static String render(String template, Map<String, Object> variables) {
    return PromptTemplate.builder().template(template).build().render(variables);
  }

  private static String evaluation(
      Optional<dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing> evaluation) {
    return evaluation
        .map(
            value ->
                "Evaluation: before="
                    + value.beforeCentipawns()
                    + "cp, after="
                    + value.afterCentipawns()
                    + "cp, swing="
                    + value.swingCentipawns()
                    + "cp, classification="
                    + value.classification())
        .orElse("Evaluation: unavailable");
  }

  private static String history(java.util.List<DialogueHistoryLine> history) {
    if (history.isEmpty()) {
      return "none";
    }
    return history.stream()
        .map(
            line ->
                "[ply "
                    + line.triggeringPly()
                    + "] "
                    + line.speakerDisplayName()
                    + ": "
                    + line.text())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("none");
  }
}
