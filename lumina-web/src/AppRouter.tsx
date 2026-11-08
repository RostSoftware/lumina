import { lazy } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { ScrollToTop } from './components/ScrollToTop';
import { AppShell } from './components/layout/AppShell';
import { LoginDialogProvider } from './components/auth/LoginDialogProvider';

import HomePage from './pages/HomePage';
import DiscoverPage from './pages/DiscoverPage';
import { NIP19Page } from './pages/NIP19Page';
import NotFound from './pages/NotFound';

const CreatePage = lazy(() => import('./pages/CreatePage'));
const NotificationsPage = lazy(() => import('./pages/NotificationsPage'));
const MessagesPage = lazy(() => import('./pages/MessagesPage'));
const ChatPage = lazy(() => import('./pages/ChatPage'));
const OpenUriPage = lazy(() => import('./pages/OpenUriPage'));
const RemoteLoginSuccess = lazy(() => import('./pages/RemoteLoginSuccess'));
const MyProfileRedirect = lazy(() => import('./pages/MyProfileRedirect'));
const SettingsPage = lazy(() => import('./pages/settings/SettingsPage'));
const EditProfilePage = lazy(() => import('./pages/settings/EditProfilePage'));
const RelaysPage = lazy(() => import('./pages/settings/RelaysPage'));
const MediaServersPage = lazy(() => import('./pages/settings/MediaServersPage'));
const WalletPage = lazy(() => import('./pages/settings/WalletPage'));
const AppearancePage = lazy(() => import('./pages/settings/AppearancePage'));
const ContentPage = lazy(() => import('./pages/settings/ContentPage'));
const AccountPage = lazy(() => import('./pages/settings/AccountPage'));

export function AppRouter() {
  return (
    <BrowserRouter>
      <LoginDialogProvider>
        <ScrollToTop />
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/discover" element={<DiscoverPage />} />
            <Route path="/create" element={<CreatePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/messages" element={<MessagesPage />} />
            <Route path="/messages/:npub" element={<ChatPage />} />
            <Route path="/profile" element={<MyProfileRedirect />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/settings/profile" element={<EditProfilePage />} />
            <Route path="/settings/relays" element={<RelaysPage />} />
            <Route path="/settings/media" element={<MediaServersPage />} />
            <Route path="/settings/wallet" element={<WalletPage />} />
            <Route path="/settings/appearance" element={<AppearancePage />} />
            <Route path="/settings/content" element={<ContentPage />} />
            <Route path="/settings/account" element={<AccountPage />} />
            <Route path="/open" element={<OpenUriPage />} />
            <Route path="/remoteloginsuccess" element={<RemoteLoginSuccess />} />
            {/* NIP-19: npub, nprofile, note, nevent, naddr (optionally nostr:-prefixed) */}
            <Route path="/:nip19" element={<NIP19Page />} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </LoginDialogProvider>
    </BrowserRouter>
  );
}
export default AppRouter;
