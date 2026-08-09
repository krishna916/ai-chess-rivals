package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;

record DialogueModelOutput(
    String text, DialogueEmotion emotion, DialogueReactionType reactionType) {}
