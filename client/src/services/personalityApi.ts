import axios from "axios";
import type { PersonalityRosterItem } from "../types/match";
import { API_BASE_URL } from "./matchApi";

const personalityApiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  headers: {
    "Content-Type": "application/json",
  },
});

export const personalityApi = {
  listSelectable: async (): Promise<PersonalityRosterItem[]> => {
    const response =
      await personalityApiClient.get<PersonalityRosterItem[]>("/personalities");
    return response.data;
  },
};
