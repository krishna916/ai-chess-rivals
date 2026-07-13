export type MatchStatus =
  "IDLE" | "NOT_STARTED" | "IN_PROGRESS" | "STOPPED" | "FINISHED";
export type ConnectionStatus =
  "CONNECTING" | "CONNECTED" | "DISCONNECTED" | "ERROR";

export interface BaseMessage {
  type: string;
}

export interface MoveResponse {
  sequenceNumber: number;
  player: "WHITE" | "BLACK";
  notation: string;
  fenAfterMove: string;
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
  sideToMove: "WHITE" | "BLACK";
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
    sideToMove: "WHITE" | "BLACK";
    fen: string;
  };
}

export interface MovePlayedMessage extends BaseMessage {
  type: "MOVE_PLAYED";
  payload: {
    ply: number;
    player: "WHITE" | "BLACK";
    notation: string;
    fen: string;
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
    sideToMove: "WHITE" | "BLACK";
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

export interface MatchActivityItem {
  id: string;
  kind: MatchActivityKind;
  sequence: number;
  player?: "WHITE" | "BLACK";
  notation?: string;
  capture?: boolean;
  check?: boolean;
  checkmate?: boolean;
  promotion?: boolean;
  result?: string;
}
