import { createContext } from 'react';

export interface LoginDialogContextType {
  /** Open the login flow. */
  openLogin: () => void;
  /**
   * Returns true when a user is logged in. Otherwise opens the login flow and
   * returns false. Use it to guard every action that needs a signer.
   */
  requireLogin: () => boolean;
}

export const LoginDialogContext = createContext<LoginDialogContextType | undefined>(undefined);
