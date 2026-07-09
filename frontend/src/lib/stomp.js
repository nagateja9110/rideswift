import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { wsUrl } from "@/lib/origin";

// A single shared STOMP client over SockJS. Subscriptions are reference-counted
// so multiple components can listen to the same topic without duplicate sockets.
let client = null;
let connected = false;
const pending = [];

function ensureClient() {
  if (client) return client;
  client = new Client({
    // Same-origin '/ws' for dev/proxied deploys; absolute VITE_WS_URL when the
    // frontend is hosted on a different origin than the API (e.g. Vercel + Render).
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      connected = true;
      pending.splice(0).forEach((fn) => fn());
    },
    onWebSocketClose: () => {
      connected = false;
    },
  });
  client.activate();
  return client;
}

export function subscribeTopic(destination, handler) {
  const c = ensureClient();
  let sub = null;

  const doSubscribe = () => {
    sub = c.subscribe(destination, (msg) => {
      try {
        handler(JSON.parse(msg.body));
      } catch {
        // Plain-string payloads (e.g. a bare status string) fall through here.
        handler(msg.body);
      }
    });
  };

  if (connected) doSubscribe();
  else pending.push(doSubscribe);

  return () => {
    sub?.unsubscribe();
  };
}
