import { useState } from "react";
import { MatchAdminControls } from "@/features/admin/MatchAdminControls";
import { OwnerTokenForm } from "@/features/admin/OwnerTokenForm";
import { getOwnerToken } from "@/features/admin/ownerToken";

export function AdminPage() {
  const [token, setToken] = useState<string | null>(() => getOwnerToken());

  return (
    <main className="min-h-screen bg-neutral-50 p-4 dark:bg-neutral-900 md:p-8">
      <div className="mx-auto flex w-full max-w-5xl flex-col items-center gap-8">
        <header className="w-full max-w-xl">
          <p className="text-sm font-medium text-muted-foreground">
            AI Chess Rivals
          </p>
          <h1 className="text-3xl font-bold tracking-tight">
            Match Operations
          </h1>
        </header>
        {token ? (
          <MatchAdminControls
            token={token}
            onLock={() => setToken(null)}
            onUnauthorized={() => setToken(null)}
          />
        ) : (
          <OwnerTokenForm onUnlock={setToken} />
        )}
      </div>
    </main>
  );
}
