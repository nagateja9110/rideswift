import { useCallback, useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { rideApi } from "@/api/endpoints";
import { subscribeTopic } from "@/lib/stomp";

// Chat for one ride: REST history (survives refresh) + live STOMP pushes,
// merged and de-duplicated by message id.
export function useRideChat(rideId, enabled) {
  const qc = useQueryClient();
  const key = ["ride", rideId, "messages"];
  const [sending, setSending] = useState(false);

  const { data: messages = [], isLoading } = useQuery({
    queryKey: key,
    queryFn: () => rideApi.messages(rideId),
    enabled: !!rideId && enabled,
  });

  const merge = useCallback(
    (incoming) => {
      qc.setQueryData(key, (prev = []) =>
        prev.some((m) => m.id === incoming.id)
          ? prev
          : [...prev, incoming].sort((a, b) =>
              a.sentAt.localeCompare(b.sentAt),
            ),
      );
    },
    [qc, key],
  );

  useEffect(() => {
    if (!rideId || !enabled) return;
    const unsub = subscribeTopic(`/topic/ride/${rideId}/messages`, merge);
    return () => unsub();
  }, [rideId, enabled, merge]);

  const send = useCallback(
    async (content) => {
      const text = content.trim();
      if (!rideId || !text) return;
      setSending(true);
      try {
        const msg = await rideApi.sendMessage(rideId, text);
        merge(msg); // optimistic-ish; the STOMP echo is de-duped by id
      } finally {
        setSending(false);
      }
    },
    [rideId, merge],
  );

  return { messages, isLoading, sending, send };
}
