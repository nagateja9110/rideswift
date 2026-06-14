import { useEffect, useRef, useState } from 'react';
import { Send, Loader2, MessageSquare } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { useRideChat } from '@/hooks/useRideChat';
import { useAuthStore } from '@/store/authStore';
import { cn } from '@/lib/utils';

function timeOf(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export function ChatPanel({
  rideId,
  open,
  onClose,
  title,
}: {
  rideId: string;
  open: boolean;
  onClose: () => void;
  title: string;
}) {
  const meId = useAuthStore((s) => s.user?.id);
  const { messages, isLoading, sending, send } = useRideChat(rideId, open);
  const [draft, setDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, open]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;
    setDraft('');
    await send(text);
  };

  return (
    <Dialog open={open} onClose={onClose} className="flex h-[70vh] max-h-[600px] flex-col p-0">
      <div className="flex items-center gap-2 border-b px-5 py-4">
        <MessageSquare className="h-5 w-5 text-primary" />
        <h3 className="font-semibold">{title}</h3>
      </div>

      <div className="flex-1 space-y-3 overflow-y-auto px-5 py-4">
        {isLoading ? (
          <div className="flex h-full items-center justify-center text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" />
          </div>
        ) : messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-2 text-center text-sm text-muted-foreground">
            <MessageSquare className="h-8 w-8 opacity-40" />
            <p>No messages yet. Say hello 👋</p>
          </div>
        ) : (
          messages.map((m) => {
            const mine = m.senderId === meId;
            return (
              <div key={m.id} className={cn('flex flex-col', mine ? 'items-end' : 'items-start')}>
                <div
                  className={cn(
                    'max-w-[75%] rounded-2xl px-3.5 py-2 text-sm',
                    mine
                      ? 'rounded-br-sm bg-primary text-primary-foreground'
                      : 'rounded-bl-sm bg-muted text-foreground'
                  )}
                >
                  {m.content}
                </div>
                <span className="mt-1 px-1 text-[10px] text-muted-foreground">
                  {!mine && `${m.senderName.split(' ')[0]} · `}
                  {timeOf(m.sentAt)}
                </span>
              </div>
            );
          })
        )}
        <div ref={endRef} />
      </div>

      <form onSubmit={submit} className="flex items-center gap-2 border-t px-4 py-3">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Type a message…"
          maxLength={1000}
          autoFocus
        />
        <button
          type="submit"
          disabled={sending || !draft.trim()}
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-primary text-primary-foreground transition-opacity disabled:opacity-50"
        >
          {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
        </button>
      </form>
    </Dialog>
  );
}
