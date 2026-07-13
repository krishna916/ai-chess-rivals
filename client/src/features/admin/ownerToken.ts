const OWNER_TOKEN_KEY = "ownerControlToken";

export const getOwnerToken = () => sessionStorage.getItem(OWNER_TOKEN_KEY);

export const setOwnerToken = (token: string) =>
  sessionStorage.setItem(OWNER_TOKEN_KEY, token.trim());

export const clearOwnerToken = () => sessionStorage.removeItem(OWNER_TOKEN_KEY);
