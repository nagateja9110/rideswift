import { useEffect, useState } from 'react';

const KEY = 'rideswift-theme';

export function useTheme() {
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem(KEY);
    return saved ? saved === 'dark' : true;
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    localStorage.setItem(KEY, dark ? 'dark' : 'light');
  }, [dark]);

  return { dark, toggle: () => setDark((d) => !d) };
}
