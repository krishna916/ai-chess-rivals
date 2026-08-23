export type MatchStatus =
  "IDLE" | "NOT_STARTED" | "IN_PROGRESS" | "STOPPED" | "FINISHED";
export type ConnectionStatus =
  "CONNECTING" | "CONNECTED" | "DISCONNECTED" | "ERROR";
export type PlayerColor = "WHITE" | "BLACK";
export type ChessPieceType =
  "PAWN" | "KNIGHT" | "BISHOP" | "ROOK" | "QUEEN" | "KING";
export type CastlingSide = "KING_SIDE" | "QUEEN_SIDE";
export type DialogueTriggerType = "GAME_START" | "MOVE" | "GAME_END";
export type DialogueEmotion =
  | "NEUTRAL"
  | "CONFIDENT"
  | "AMUSED"
  | "ANNOYED"
  | "CALM"
  | "TRIUMPHANT"
  | "DEFIANT";
export type DialogueReactionType =
  "GAME_START" | "MOVE_REACTION" | "VICTORY" | "DEFEAT" | "DRAW";
export type AiResponseSource =
  "REMOTE_PRIMARY" | "REMOTE_FALLBACK" | "DETERMINISTIC_FALLBACK";

export interface BaseMessage {
  type: string;
}

export interface MoveResponse {
  sequenceNumber: number;
  player: PlayerColor;
  notation: string;
  fenAfterMove: string;
  movingPiece: ChessPieceType;
  movingPieceColor: PlayerColor;
  sourceSquare: string;
  destinationSquare: string;
  capturedPiece: ChessPieceType | null;
  capturedPieceColor: PlayerColor | null;
  promotedPiece: ChessPieceType | null;
  castlingSide: CastlingSide | null;
  capture: boolean;
  check: boolean;
  checkmate: boolean;
  promotion: boolean;
}

export interface StartAvailability {
  allowed: boolean;
  blockedBy:
    | "MATCH_ALREADY_RUNNING"
    | "MATCH_COOLDOWN_ACTIVE"
    | "MATCH_DAILY_LIMIT_REACHED"
    | null;
  retryAfterSeconds: number;
  dailyStartsAccepted: number;
  dailyStartLimit: number;
}

export interface DialogueResponse {
  id: number;
  matchId: string;
  triggerType: DialogueTriggerType;
  triggerPly: number;
  personalityKey: string;
  personalityDisplayName: string;
  text: string;
  emotion: DialogueEmotion;
  reactionType: DialogueReactionType;
  source: AiResponseSource;
  createdAt: string;
}

export interface PersonalityRosterItem {
  key: string;
  displayName: string;
  description: string;
  avatarRef: string | null;
}

export interface MatchPersonality {
  key: string;
  displayName: string;
}

export interface StartMatchRequest {
  whitePersonalityKey: string;
  blackPersonalityKey: string;
}

export interface MatchResponse {
  matchId: string;
  whitePersonality: MatchPersonality;
  blackPersonality: MatchPersonality;
  sideToMove: PlayerColor;
  fen: string;
  moves: MoveResponse[];
  status: MatchStatus;
  result: string | null;
  running: boolean;
  startAvailability: StartAvailability;
  dialogue: DialogueResponse[];
}

export interface MatchStateMessage extends BaseMessage {
  type: "MATCH_STATE";
  payload: MatchResponse;
}

export interface MatchStartedMessage extends BaseMessage {
  type: "MATCH_STARTED";
  payload: {
    matchId: string;
    whitePersonality: MatchPersonality;
    blackPersonality: MatchPersonality;
    sideToMove: PlayerColor;
    fen: string;
  };
}

export interface DialoguePlayedMessage extends BaseMessage {
  type: "DIALOGUE_PLAYED";
  payload: DialogueResponse;
}

export interface MovePlayedMessage extends BaseMessage {
  type: "MOVE_PLAYED";
  payload: {
    ply: number;
    player: PlayerColor;
    notation: string;
    fen: string;
    movingPiece: ChessPieceType;
    movingPieceColor: PlayerColor;
    sourceSquare: string;
    destinationSquare: string;
    capturedPiece: ChessPieceType | null;
    capturedPieceColor: PlayerColor | null;
    promotedPiece: ChessPieceType | null;
    castlingSide: CastlingSide | null;
    capture: boolean;
    check: boolean;
    checkmate: boolean;
    promotion: boolean;
  };
}

export interface MatchFinishedMessage extends BaseMessage {
  type: "MATCH_FINISHED";
  payload: {
    result: string;
    fen: string;
    totalPlies: number;
  };
}

export interface MatchStoppedMessage extends BaseMessage {
  type: "MATCH_STOPPED";
  payload: {
    sideToMove: PlayerColor;
    fen: string;
    totalPlies: number;
  };
}

export interface NoMatchMessage extends BaseMessage {
  type: "NO_MATCH";
  payload: Record<string, never>;
}

export type MatchStreamMessage =
  | MatchStateMessage
  | MatchStartedMessage
  | MovePlayedMessage
  | MatchStoppedMessage
  | MatchFinishedMessage
  | DialoguePlayedMessage
  | NoMatchMessage;

export type MatchActivityKind =
  "MATCH_STARTED" | "MOVE" | "DIALOGUE" | "MATCH_FINISHED";

export interface MatchStartedActivityItem {
  id: string;
  kind: "MATCH_STARTED";
  sequence: number;
}

export interface MoveActivityItem {
  id: string;
  kind: "MOVE";
  sequence: number;
  player: PlayerColor;
  notation: string;
  movingPiece: ChessPieceType;
  movingPieceColor: PlayerColor;
  sourceSquare: string;
  destinationSquare: string;
  capturedPiece?: ChessPieceType;
  capturedPieceColor?: PlayerColor;
  promotedPiece?: ChessPieceType;
  castlingSide?: CastlingSide;
  capture: boolean;
  check: boolean;
  checkmate: boolean;
  promotion: boolean;
  isNew: boolean;
}

export interface DialogueActivityItem {
  id: string;
  kind: "DIALOGUE";
  sequence: number;
  dialogueId: number;
  triggerType: DialogueTriggerType;
  personalityKey: string;
  personalityDisplayName: string;
  side: PlayerColor | null;
  text: string;
  emotion: DialogueEmotion;
  reactionType: DialogueReactionType;
  createdAt: string;
}

export interface MatchFinishedActivityItem {
  id: string;
  kind: "MATCH_FINISHED";
  sequence: number;
  result: string;
}

export type MatchActivityItem =
  | MatchStartedActivityItem
  | MoveActivityItem
  | DialogueActivityItem
  | MatchFinishedActivityItem;
