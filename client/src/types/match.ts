export type MatchStatus =
  "IDLE" | "NOT_STARTED" | "IN_PROGRESS" | "STOPPED" | "FINISHED";
export type ConnectionStatus =
  "CONNECTING" | "CONNECTED" | "DISCONNECTED" | "ERROR";
export type PlayerColor = "WHITE" | "BLACK";
export type ChessPieceType =
  "PAWN" | "KNIGHT" | "BISHOP" | "ROOK" | "QUEEN" | "KING";
export type CastlingSide = "KING_SIDE" | "QUEEN_SIDE";

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

export interface MatchResponse {
  sideToMove: PlayerColor;
  fen: string;
  moves: MoveResponse[];
  status: MatchStatus;
  result: string | null;
  running: boolean;
  startAvailability: StartAvailability;
}

export interface MatchStateMessage extends BaseMessage {
  type: "MATCH_STATE";
  payload: MatchResponse;
}

export interface MatchStartedMessage extends BaseMessage {
  type: "MATCH_STARTED";
  payload: {
    sideToMove: PlayerColor;
    fen: string;
  };
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
  | NoMatchMessage;

export type MatchActivityKind = "MATCH_STARTED" | "MOVE" | "MATCH_FINISHED";

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

export interface MatchFinishedActivityItem {
  id: string;
  kind: "MATCH_FINISHED";
  sequence: number;
  result: string;
}

export type MatchActivityItem =
  MatchStartedActivityItem | MoveActivityItem | MatchFinishedActivityItem;
