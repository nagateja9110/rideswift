import { useEffect, useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { geocodeApi } from '@/api/endpoints';
import type { GeocodeResult } from '@/types';
import { cn } from '@/lib/utils';

export interface SelectedPlace {
  lat: number;
  lng: number;
  address: string;
}

export function AddressAutocomplete({
  icon,
  label,
  value,
  active,
  placeholder,
  onSelect,
  onFocus,
}: {
  icon: React.ReactNode;
  label: string;
  value?: string; // current address (set by selection or a map tap)
  active?: boolean;
  placeholder?: string;
  onSelect: (place: SelectedPlace) => void;
  onFocus?: () => void;
}) {
  const [query, setQuery] = useState(value ?? '');
  const [results, setResults] = useState<GeocodeResult[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [highlight, setHighlight] = useState(0);

  const focusedRef = useRef(false);
  const justSelectedRef = useRef(false);
  const boxRef = useRef<HTMLDivElement>(null);

  // Reflect externally-set addresses (map tap, sample trips, reset) when not editing.
  useEffect(() => {
    if (!focusedRef.current) setQuery(value ?? '');
  }, [value]);

  // Debounced search. Only search while the user is actively typing (focused) —
  // this keeps an externally-set value (map tap / selection) from triggering a
  // lookup. Once results arrive they open the dropdown regardless of momentary
  // focus changes; outside-click / Escape / selection close it.
  useEffect(() => {
    if (justSelectedRef.current) {
      justSelectedRef.current = false;
      return;
    }
    if (!focusedRef.current) return;
    const q = query.trim();
    if (q.length < 3) {
      setResults([]);
      setOpen(false);
      setLoading(false);
      return;
    }
    setLoading(true);
    const id = setTimeout(async () => {
      try {
        const res = await geocodeApi.search(q, 6);
        setResults(res);
        setHighlight(0);
        setOpen(res.length > 0);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, 350);
    return () => clearTimeout(id);
  }, [query]);

  // Close on outside click.
  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  const choose = (r: GeocodeResult) => {
    justSelectedRef.current = true;
    setQuery(r.displayName);
    setOpen(false);
    setResults([]);
    onSelect({ lat: r.lat, lng: r.lng, address: r.displayName });
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (!open || results.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlight((h) => Math.min(h + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      choose(results[highlight]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  return (
    <div ref={boxRef} className="relative">
      <div
        className={cn(
          'flex items-center gap-3 rounded-lg border px-3 py-2.5',
          active ? 'border-primary ring-1 ring-primary' : 'border-border'
        )}
      >
        {icon}
        <div className="min-w-0 flex-1">
          <p className="text-xs text-muted-foreground">{label}</p>
          <input
            value={query}
            placeholder={placeholder ?? 'Search address…'}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => {
              focusedRef.current = true;
              onFocus?.();
              if (results.length) setOpen(true);
            }}
            onBlur={() => {
              focusedRef.current = false;
            }}
            onKeyDown={onKeyDown}
            className="w-full bg-transparent text-sm font-medium outline-none placeholder:font-normal placeholder:text-muted-foreground"
          />
        </div>
        {loading && <Loader2 className="h-4 w-4 shrink-0 animate-spin text-muted-foreground" />}
      </div>

      {open && results.length > 0 && (
        <ul className="absolute z-[500] mt-1 max-h-64 w-full overflow-y-auto rounded-lg border bg-card shadow-xl">
          {results.map((r, i) => (
            <li key={`${r.lat},${r.lng},${i}`}>
              <button
                type="button"
                onMouseDown={(e) => e.preventDefault()} // keep input from blurring first
                onClick={() => choose(r)}
                onMouseEnter={() => setHighlight(i)}
                className={cn(
                  'flex w-full flex-col items-start gap-0.5 px-3 py-2 text-left text-sm',
                  i === highlight ? 'bg-accent' : 'hover:bg-accent/60'
                )}
              >
                <span className="font-medium">{r.name}</span>
                <span className="line-clamp-1 text-xs text-muted-foreground">{r.displayName}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
