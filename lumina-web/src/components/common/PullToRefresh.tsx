import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ArrowDown } from 'lucide-react';
import { Spinner } from './States';

const THRESHOLD = 70;

/** Touch pull-to-refresh on the window scroller (mobile). */
export function PullToRefresh({ onRefresh, children }: { onRefresh: () => Promise<unknown>; children: ReactNode }) {
  const [pull, setPull] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const start = useRef<number | null>(null);
  const pullRef = useRef(0);
  const refreshRef = useRef(onRefresh);
  useEffect(() => {
    refreshRef.current = onRefresh;
  }, [onRefresh]);

  useEffect(() => {
    const onStart = (e: TouchEvent) => {
      start.current = window.scrollY <= 0 ? e.touches[0].clientY : null;
    };
    const onMove = (e: TouchEvent) => {
      if (start.current === null) return;
      const delta = e.touches[0].clientY - start.current;
      if (delta > 0 && window.scrollY <= 0) {
        pullRef.current = Math.min(120, delta * 0.5);
        setPull(pullRef.current);
      } else {
        pullRef.current = 0;
        setPull(0);
      }
    };
    const onEnd = async () => {
      const distance = pullRef.current;
      start.current = null;
      pullRef.current = 0;
      setPull(0);
      if (distance >= THRESHOLD) {
        setRefreshing(true);
        try {
          await refreshRef.current();
        } finally {
          setRefreshing(false);
        }
      }
    };
    window.addEventListener('touchstart', onStart, { passive: true });
    window.addEventListener('touchmove', onMove, { passive: true });
    window.addEventListener('touchend', onEnd);
    return () => {
      window.removeEventListener('touchstart', onStart);
      window.removeEventListener('touchmove', onMove);
      window.removeEventListener('touchend', onEnd);
    };
  }, []);

  const height = refreshing ? 48 : pull;
  return (
    <>
      <div className="flex items-end justify-center overflow-hidden transition-[height] duration-150 ease-out" style={{ height }} aria-hidden={!refreshing}>
        <div className="pb-3">
          {refreshing ? (
            <Spinner />
          ) : (
            <ArrowDown className="size-5 text-muted-foreground transition-transform" style={{ transform: pull >= THRESHOLD ? 'rotate(180deg)' : undefined }} />
          )}
        </div>
      </div>
      {children}
    </>
  );
}
