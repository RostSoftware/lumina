import { useEffect, useRef } from 'react';
import { Spinner } from './States';

/** Calls `onLoadMore` when scrolled into view (IntersectionObserver). */
export function InfiniteTrigger({ onLoadMore, hasMore, loading }: { onLoadMore: () => void; hasMore: boolean; loading: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  const callback = useRef(onLoadMore);
  useEffect(() => {
    callback.current = onLoadMore;
  }, [onLoadMore]);

  useEffect(() => {
    const el = ref.current;
    if (!el || !hasMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) callback.current();
      },
      { rootMargin: '800px 0px' },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasMore]);

  if (!hasMore) return <div className="h-8" />;
  return (
    <div ref={ref} className="flex h-16 items-center justify-center">
      {loading && <Spinner />}
    </div>
  );
}
