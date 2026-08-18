import { Button } from "@/components/ui/button";
import type { PersonalityRosterItem } from "@/types/match";

interface RivalrySetupProps {
  roster: PersonalityRosterItem[];
  whitePersonalityKey: string;
  blackPersonalityKey: string;
  disabled: boolean;
  onWhiteChange: (key: string) => void;
  onBlackChange: (key: string) => void;
  onRandomize: () => void;
}

const selectClassName =
  "w-full rounded-md border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50";

export function RivalrySetup({
  roster,
  whitePersonalityKey,
  blackPersonalityKey,
  disabled,
  onWhiteChange,
  onBlackChange,
  onRandomize,
}: RivalrySetupProps) {
  const white = roster.find((item) => item.key === whitePersonalityKey);
  const black = roster.find((item) => item.key === blackPersonalityKey);

  return (
    <div className="space-y-3">
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-1 text-sm font-medium">
          <span>White personality</span>
          <select
            aria-label="White personality"
            className={selectClassName}
            disabled={disabled}
            value={whitePersonalityKey}
            onChange={(event) => onWhiteChange(event.target.value)}
          >
            {roster.map((personality) => (
              <option
                key={personality.key}
                value={personality.key}
                disabled={personality.key === blackPersonalityKey}
              >
                {personality.displayName}
              </option>
            ))}
          </select>
          {white && (
            <span className="block text-sm text-muted-foreground">
              {white.description}
            </span>
          )}
        </label>

        <label className="space-y-1 text-sm font-medium">
          <span>Black personality</span>
          <select
            aria-label="Black personality"
            className={selectClassName}
            disabled={disabled}
            value={blackPersonalityKey}
            onChange={(event) => onBlackChange(event.target.value)}
          >
            {roster.map((personality) => (
              <option
                key={personality.key}
                value={personality.key}
                disabled={personality.key === whitePersonalityKey}
              >
                {personality.displayName}
              </option>
            ))}
          </select>
          {black && (
            <span className="block text-sm text-muted-foreground">
              {black.description}
            </span>
          )}
        </label>
      </div>
      <Button
        type="button"
        variant="outline"
        disabled={disabled}
        onClick={onRandomize}
      >
        Randomize Rivalry
      </Button>
    </div>
  );
}
