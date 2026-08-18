import axios from "axios";
import type { MatchResponse, StartMatchRequest } from "../types/match";
import { API_BASE_URL } from "./matchApi";

const adminApiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  headers: {
    "Content-Type": "application/json",
  },
});

const authorization = (token: string) => ({
  headers: { Authorization: `Bearer ${token}` },
});

export const adminMatchApi = {
  startMatch: async (
    token: string,
    request: StartMatchRequest,
  ): Promise<MatchResponse> => {
    const response = await adminApiClient.post<MatchResponse>(
      "/match/start",
      request,
      authorization(token),
    );
    return response.data;
  },

  stopMatch: async (token: string): Promise<MatchResponse> => {
    const response = await adminApiClient.post<MatchResponse>(
      "/match/stop",
      undefined,
      authorization(token),
    );
    return response.data;
  },
};
