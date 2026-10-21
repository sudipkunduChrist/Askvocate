import { useCallback, useEffect, useRef, useState } from 'react';
import {
  approveDocument as approveDocumentRequest,
  approveLawyer as approveLawyerRequest,
  fetchPendingDocuments,
  fetchPendingLawyers,
  rejectDocument as rejectDocumentRequest,
  rejectLawyer as rejectLawyerRequest,
} from '../api/client';
import { isAuthError, toErrorMessage } from '../api/errors';
import type { Lawyer, LawyerKind, PendingDocument } from '../api/types';

interface DataState {
  documents: PendingDocument[];
  freshers: Lawyer[];
  experienced: Lawyer[];
  loading: boolean;
  loaded: boolean;
  error: string | null;
  lastRefreshed: Date | null;
  verifiedToday: number;
}

export interface AdminData extends DataState {
  refresh: () => Promise<void>;
  approveDocument: (id: string, notes?: string) => Promise<void>;
  rejectDocument: (id: string, reason: string) => Promise<void>;
  approveLawyer: (kind: LawyerKind, id: string) => Promise<void>;
  rejectLawyer: (kind: LawyerKind, id: string, reason: string) => Promise<void>;
}

const initialState: DataState = {
  documents: [],
  freshers: [],
  experienced: [],
  loading: true,
  loaded: false,
  error: null,
  lastRefreshed: null,
  verifiedToday: 0,
};

export function useAdminData(
  token: string | null,
  onSessionExpired: () => void,
): AdminData {
  const [state, setState] = useState<DataState>(initialState);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    if (!token) return;
    const currentRequest = ++requestId.current;

    setState((previous) => ({ ...previous, loading: true, error: null }));

    try {
      const [documents, lawyers] = await Promise.all([
        fetchPendingDocuments(),
        fetchPendingLawyers(),
      ]);
      if (currentRequest !== requestId.current) return;

      setState((previous) => ({
        ...previous,
        documents,
        freshers: lawyers.freshers,
        experienced: lawyers.experienced,
        loading: false,
        loaded: true,
        error: null,
        lastRefreshed: new Date(),
      }));
    } catch (error) {
      if (currentRequest !== requestId.current) return;
      if (isAuthError(error)) {
        onSessionExpired();
        return;
      }
      setState((previous) => ({
        ...previous,
        loading: false,
        error: toErrorMessage(error),
      }));
    }
  }, [token, onSessionExpired]);

  useEffect(() => {
    void load();
  }, [load]);

  const guard = useCallback(
    async <T,>(action: () => Promise<T>): Promise<T> => {
      try {
        return await action();
      } catch (error) {
        if (isAuthError(error)) onSessionExpired();
        throw error;
      }
    },
    [onSessionExpired],
  );

  const approveDocument = useCallback(
    async (id: string, notes?: string) => {
      await guard(() => approveDocumentRequest(id, notes));
      setState((previous) => ({
        ...previous,
        documents: previous.documents.filter((document) => document.id !== id),
        verifiedToday: previous.verifiedToday + 1,
      }));
    },
    [guard],
  );

  const rejectDocument = useCallback(
    async (id: string, reason: string) => {
      await guard(() => rejectDocumentRequest(id, reason));
      setState((previous) => ({
        ...previous,
        documents: previous.documents.filter((document) => document.id !== id),
      }));
    },
    [guard],
  );

  const patchLawyer = useCallback(
    (kind: LawyerKind, id: string, patch: Partial<Lawyer>) => {
      setState((previous) => ({
        ...previous,
        freshers:
          kind === 'fresher'
            ? previous.freshers.map((lawyer) =>
                lawyer.id === id ? { ...lawyer, ...patch } : lawyer,
              )
            : previous.freshers,
        experienced:
          kind === 'experienced'
            ? previous.experienced.map((lawyer) =>
                lawyer.id === id ? { ...lawyer, ...patch } : lawyer,
              )
            : previous.experienced,
      }));
    },
    [],
  );

  const approveLawyer = useCallback(
    async (kind: LawyerKind, id: string) => {
      await guard(() => approveLawyerRequest(kind, id));
      patchLawyer(kind, id, {
        verificationStatus: 'VERIFIED',
        verifiedAt: Date.now(),
        rejectionReason: null,
      });
      setState((previous) => ({
        ...previous,
        verifiedToday: previous.verifiedToday + 1,
      }));
    },
    [guard, patchLawyer],
  );

  const rejectLawyer = useCallback(
    async (kind: LawyerKind, id: string, reason: string) => {
      await guard(() => rejectLawyerRequest(kind, id, reason));
      patchLawyer(kind, id, {
        verificationStatus: 'REJECTED',
        rejectionReason: reason.trim(),
      });
    },
    [guard, patchLawyer],
  );

  return {
    ...state,
    refresh: load,
    approveDocument,
    rejectDocument,
    approveLawyer,
    rejectLawyer,
  };
}
