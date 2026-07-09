import { useState } from "react";
import { Bell, CheckCheck } from "lucide-react";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { notificationApi } from "@/api/endpoints";
import { Button } from "@/components/ui/button";
import { relativeTime } from "@/lib/utils";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const qc = useQueryClient();
  const { data = [] } = useQuery({
    queryKey: ["notifications"],
    queryFn: notificationApi.list,
    refetchInterval: 15000,
  });
  const unread = data.filter((n) => !n.read).length;

  const markAll = useMutation({
    mutationFn: notificationApi.markAllRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-md p-2 text-muted-foreground hover:bg-accent hover:text-foreground"
        aria-label="Notifications"
      >
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-bold text-destructive-foreground">
            {unread}
          </span>
        )}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-40 mt-2 w-80 overflow-hidden rounded-lg border bg-card shadow-xl animate-fade-in">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <p className="text-sm font-semibold">Notifications</p>
              {unread > 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => markAll.mutate()}
                  className="h-7 gap-1 text-xs"
                >
                  <CheckCheck className="h-3.5 w-3.5" /> Mark all read
                </Button>
              )}
            </div>
            <div className="max-h-80 overflow-y-auto">
              {data.length === 0 && (
                <p className="px-4 py-8 text-center text-sm text-muted-foreground">
                  No notifications
                </p>
              )}
              {data.map((n) => (
                <div
                  key={n.id}
                  className={`border-b px-4 py-3 text-sm last:border-0 ${n.read ? "opacity-60" : "bg-accent/40"}`}
                >
                  <p>{n.message}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {relativeTime(n.createdAt)}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
