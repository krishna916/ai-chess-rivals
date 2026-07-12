import axios from 'axios';
import type { MatchResponse } from '../types/match';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8082/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const matchApi = {
  startMatch: async (): Promise<MatchResponse> => {
    const response = await apiClient.post<MatchResponse>('/match/start');
    return response.data;
  },
  
  stopMatch: async (): Promise<MatchResponse> => {
    const response = await apiClient.post<MatchResponse>('/match/stop');
    return response.data;
  },

  getCurrentMatch: async (): Promise<MatchResponse> => {
    const response = await apiClient.get<MatchResponse>('/match');
    return response.data;
  }
};
