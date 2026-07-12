import { Badge } from "@/components/ui/badge";
import { useMatchViewerStore } from "@/store/matchViewerStore";

export function ConnectionBadge() {
  const status = useMatchViewerStore((s) => s.connectionStatus);
  
  if (status === "CONNECTED") return null;
  
  const variants = {
    CONNECTING: "bg-blue-100 text-blue-800",
    DISCONNECTED: "bg-yellow-100 text-yellow-800",
    ERROR: "bg-red-100 text-red-800",
  };
  
  const labels = {
    CONNECTING: "Connecting to live match...",
    DISCONNECTED: "Connection lost. Reconnecting...",
    ERROR: "Connection Error",
  };

  return (
    <Badge variant="outline" className={variants[status]}>
      {labels[status]}
    </Badge>
  );
}
