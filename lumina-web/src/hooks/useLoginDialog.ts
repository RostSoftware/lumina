import { useContext } from 'react';
import { LoginDialogContext } from '@/contexts/LoginDialogContext';

export function useLoginDialog() {
  const ctx = useContext(LoginDialogContext);
  if (!ctx) throw new Error('useLoginDialog must be used inside LoginDialogProvider');
  return ctx;
}
