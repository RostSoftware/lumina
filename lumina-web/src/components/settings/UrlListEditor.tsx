import { useState, type FormEvent, type ReactNode } from 'react';
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

/** Editable ordered list of URLs (relays / servers). */
export function UrlListEditor({
  items,
  onChange,
  normalize,
  placeholder,
  renderExtra,
  reorder,
  emptyText,
}: {
  items: string[];
  onChange: (items: string[]) => void;
  normalize: (value: string) => string | undefined;
  placeholder: string;
  renderExtra?: (item: string) => ReactNode;
  reorder?: boolean;
  emptyText: string;
}) {
  const [value, setValue] = useState('');
  const [error, setError] = useState('');

  const add = (e: FormEvent) => {
    e.preventDefault();
    const url = normalize(value);
    if (!url) {
      setError('Enter a valid secure URL.');
      return;
    }
    if (items.includes(url)) {
      setError('Already in the list.');
      return;
    }
    onChange([...items, url]);
    setValue('');
    setError('');
  };

  const move = (i: number, d: number) => {
    const next = [...items];
    [next[i], next[i + d]] = [next[i + d], next[i]];
    onChange(next);
  };

  return (
    <div className="space-y-2">
      {items.length === 0 ? (
        <p className="rounded-lg border border-dashed p-4 text-center text-meta text-muted-foreground">{emptyText}</p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {items.map((item, i) => (
            <li key={item} className="flex min-h-12 items-center gap-2 px-3 py-1">
              <span className="min-w-0 flex-1 truncate font-mono text-meta" title={item}>{item.replace(/^(wss|https):\/\//, '').replace(/\/$/, '')}</span>
              {renderExtra?.(item)}
              {reorder && (
                <>
                  <Button type="button" variant="ghost" size="icon" className="size-9" aria-label="Move up" disabled={i === 0} onClick={() => move(i, -1)}><ArrowUp /></Button>
                  <Button type="button" variant="ghost" size="icon" className="size-9" aria-label="Move down" disabled={i === items.length - 1} onClick={() => move(i, 1)}><ArrowDown /></Button>
                </>
              )}
              <Button type="button" variant="ghost" size="icon" className="size-9 text-destructive" aria-label={`Remove ${item}`} onClick={() => onChange(items.filter((x) => x !== item))}><Trash2 /></Button>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={add} className="flex gap-2">
        <Input value={value} onChange={(e) => { setValue(e.target.value); setError(''); }} placeholder={placeholder} className="h-11 font-mono text-meta" aria-label={placeholder} aria-invalid={!!error} />
        <Button type="submit" variant="outline" className="h-11" disabled={!value.trim()}><Plus /> Add</Button>
      </form>
      {error && <p className="text-meta text-destructive">{error}</p>}
    </div>
  );
}
