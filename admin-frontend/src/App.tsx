import { useCallback, useEffect, useRef, useState } from 'react';
import {
  clearStoredToken,
  getStoredToken,
  storeToken,
} from './api/client';
import { Shell } from './components/Shell';
import { Toast, type ToastData, type ToastTone } from './components/Toast';
import { useAdminData } from './hooks/useAdminData';
import type { View } from './navigation';
import { LawyerApprovalsScreen } from './screens/LawyerApprovalsScreen';
import { LoginScreen } from './screens/LoginScreen';
import { OverviewScreen } from './screens/OverviewScreen';
import { PanReviewScreen } from './screens/PanReviewScreen';

function App() {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  const [view, setView] = useState<View>('overview');
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastData | null>(null);
  const toastTimer = useRef<number | null>(null);

  const dismissToast = useCallback(() => {
    if (toastTimer.current !== null) {
      window.clearTimeout(toastTimer.current);
      toastTimer.current = null;
    }
    setToast(null);
  }, []);

  const notify = useCallback((message: string, tone: ToastTone = 'success') => {
    if (toastTimer.current !== null) {
      window.clearTimeout(toastTimer.current);
    }
    const id = Date.now();
    setToast({ id, message, tone });
    toastTimer.current = window.setTimeout(() => {
      setToast((current) => (current && current.id === id ? null : current));
      toastTimer.current = null;
    }, 4600);
  }, []);

  useEffect(
    () => () => {
      if (toastTimer.current !== null) {
        window.clearTimeout(toastTimer.current);
      }
    },
    [],
  );

  const handleSessionExpired = useCallback(() => {
    clearStoredToken();
    setToken(null);
    setSessionNotice('Session expired. Sign in again.');
    setToast(null);
  }, []);

  const data = useAdminData(token, handleSessionExpired);

  const handleSignedIn = useCallback((nextToken: string) => {
    storeToken(nextToken);
    setSessionNotice(null);
    setToken(nextToken);
  }, []);

  const handleSignOut = useCallback(() => {
    clearStoredToken();
    setToken(null);
    setSessionNotice(null);
    setView('overview');
    setSelectedDocId(null);
    setToast(null);
  }, []);

  const handleNavigate = useCallback((nextView: View) => {
    setView(nextView);
  }, []);

  const handleOpenDocument = useCallback((id: string) => {
    setSelectedDocId(id);
    setView('pan');
  }, []);

  if (!token) {
    return (
      <LoginScreen sessionMessage={sessionNotice} onSignedIn={handleSignedIn} />
    );
  }

  const pendingLawyerCount =
    data.freshers.filter(
      (lawyer) =>
        lawyer.verificationStatus === 'PENDING' ||
        lawyer.verificationStatus === 'UNDER_VERIFICATION',
    ).length +
    data.experienced.filter(
      (lawyer) =>
        lawyer.verificationStatus === 'PENDING' ||
        lawyer.verificationStatus === 'UNDER_VERIFICATION',
    ).length;

  return (
    <>
      <Shell
        view={view}
        onViewChange={handleNavigate}
        panCount={data.loaded ? data.documents.length : undefined}
        lawyerCount={data.loaded ? pendingLawyerCount : undefined}
        lastRefreshed={data.lastRefreshed}
        loadError={data.error}
        refreshing={data.loading}
        onRefresh={() => {
          void data.refresh();
        }}
        onSignOut={handleSignOut}
      >
        {view === 'overview' && (
          <OverviewScreen
            data={data}
            onOpenDocument={handleOpenDocument}
            onNavigate={handleNavigate}
          />
        )}
        {view === 'pan' && (
          <PanReviewScreen
            documents={data.documents}
            selectedId={selectedDocId}
            onSelect={setSelectedDocId}
            loading={data.loading}
            loaded={data.loaded}
            error={data.error}
            onRetry={() => {
              void data.refresh();
            }}
            onApprove={data.approveDocument}
            onReject={data.rejectDocument}
            notify={notify}
          />
        )}
        {view === 'lawyers' && (
          <LawyerApprovalsScreen
            freshers={data.freshers}
            experienced={data.experienced}
            loading={data.loading}
            loaded={data.loaded}
            error={data.error}
            onRetry={() => {
              void data.refresh();
            }}
            onApprove={data.approveLawyer}
            onReject={data.rejectLawyer}
            notify={notify}
          />
        )}
      </Shell>
      <Toast toast={toast} onDismiss={dismissToast} />
    </>
  );
}

export default App;
