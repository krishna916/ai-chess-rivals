import axios from "axios";
import type { MatchResponse } from "../types/match";

export const API_BASE_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8082/api/v1";

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  headers: {
    "Content-Type": "application/json",
  },
});

export const matchApi = {
  getCurrentMatch: async (): Promise<MatchResponse> => {
    const response = await apiClient.get<MatchResponse>("/match");
    return response.data;
  },
};
