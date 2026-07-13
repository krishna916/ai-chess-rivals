import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { setOwnerToken } from "./ownerToken";

interface OwnerTokenFormProps {
  onUnlock: (token: string) => void;
}

export function OwnerTokenForm({ onUnlock }: OwnerTokenFormProps) {
  const [token, setToken] = useState("");
  const [error, setError] = useState<string>();

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const trimmedToken = token.trim();
    if (!trimmedToken) {
      setError("Enter an owner control token.");
      return;
    }
    setOwnerToken(trimmedToken);
    onUnlock(trimmedToken);
  };

  return (
    <Card className="w-full max-w-md shadow-lg">
      <CardHeader>
        <CardTitle>Unlock Owner Controls</CardTitle>
        <CardDescription>
          Enter the server-configured token to start or stop matches.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label
              className="text-sm font-medium"
              htmlFor="owner-control-token"
            >
              Owner control token
            </label>
            <input
              id="owner-control-token"
              type="password"
              autoComplete="current-password"
              value={token}
              onChange={(event) => setToken(event.target.value)}
              className="h-9 w-full rounded-lg border bg-background px-3 outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
            />
            <p className="text-xs text-muted-foreground">
              Stored only for this browser session.
            </p>
          </div>
          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}
          <Button className="w-full" type="submit">
            Unlock Controls
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
