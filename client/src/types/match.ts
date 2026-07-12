export type MatchStatus = "IDLE" | "IN_PROGRESS" | "FINISHED";
export type ConnectionStatus = "CONNECTING" | "CONNECTED" | "DISCONNECTED" | "ERROR";

export interface MatchStateData {
  fen: string;
  turn: "WHITE" | "BLACK";
  moveCount: number;
  result?: string;
}

// Map the messages coming from WebSocket
export interface BaseMessage {
  type: string;
}

export interface MatchStateMessage extends BaseMessage {
  type: "MATCH_STATE";
  status: MatchStatus;
  state?: MatchStateData;
}

export interface MatchStartedMessage extends BaseMessage {
  type: "MATCH_STARTED";
  state: MatchStateData;
}

export interface MovePlayedMessage extends BaseMessage {
  type: "MOVE_PLAYED";
  state: MatchStateData;
  move: string;
}

export interface MatchFinishedMessage extends BaseMessage {
  type: "MATCH_FINISHED";
  state: MatchStateData;
}

export interface NoMatchMessage extends BaseMessage {
  type: "NO_MATCH";
}

export type MatchStreamMessage = 
  | MatchStateMessage
  | MatchStartedMessage
  | MovePlayedMessage
  | MatchFinishedMessage
  | NoMatchMessage;
