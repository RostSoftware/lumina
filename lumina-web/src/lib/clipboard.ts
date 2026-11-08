import { toast } from '@/hooks/useToast';

/** Copy text and confirm with a toast. */
export async function copyText(text: string, label = 'Copied') {
  try {
    await navigator.clipboard.writeText(text);
    toast({ title: label });
  } catch {
    toast({ title: 'Could not copy', variant: 'destructive' });
  }
}

