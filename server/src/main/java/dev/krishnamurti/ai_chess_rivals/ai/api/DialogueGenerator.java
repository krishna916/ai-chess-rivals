package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.List;
import java.util.Optional;

public interface DialogueGenerator {

  List<GeneratedDialogue> generateStart(DialogueStartRequest request);

  Optional<GeneratedDialogue> generateMove(DialogueMoveRequest request);

  List<GeneratedDialogue> generateEnd(DialogueEndRequest request);
}
