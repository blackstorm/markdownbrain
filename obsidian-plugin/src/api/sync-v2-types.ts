/**
 * Sync V2 Protocol Types
 * 
 * New simplified sync protocol with plan/commit pattern:
 * 1. Client sends plan (manifest or ops) to server
 * 2. Server returns what needs to be uploaded
 * 3. Client commits files and finalizes
 */

// =============================================================================
// Plan Request/Response
// =============================================================================

export type SyncMode = 'incremental' | 'full';
export type OpType = 'upsert' | 'delete';

export interface SyncOp {
  rev: number;
  op: OpType;
  fileId: string;
  path?: string;
  hash?: string;
  size?: number;
  ifMatchHash?: string;
}

export interface ManifestEntry {
  fileId: string;
  hash: string;
}

export interface PlanRequest {
  mode: SyncMode;
  baseRev?: number;
  ops?: SyncOp[];
  manifest?: ManifestEntry[];
}

export interface FileNeedUpload {
  fileId: string;
  hash: string;
}

export interface DeleteResult {
  fileId: string;
  status: 'accepted' | 'rejected-precondition' | 'not-found';
  reason?: string;
}

export interface OrphanCandidate {
  fileId: string;
  path: string;
}

export interface PlanResponse {
  success: boolean;
  syncToken?: string;
  serverState?: {
    lastAppliedRev: number;
  };
  needUpload?: FileNeedUpload[];
  alreadyHave?: FileNeedUpload[];
  deleteResults?: DeleteResult[];
  orphanCandidates?: OrphanCandidate[];
  error?: string;
  serverLastAppliedRev?: number;
}

// =============================================================================
// Commit Request/Response
// =============================================================================

export interface FileUpload {
  fileId: string;
  path: string;
  hash: string;
  content: string;
  metadata?: Record<string, unknown>;
}

export interface CommitRequest {
  syncToken: string;
  files?: FileUpload[];
  finalize: boolean;
}

export interface FileResult {
  fileId: string;
  status: 'stored' | 'deleted' | 'error';
  error?: string;
}

export interface CommitResponse {
  success: boolean;
  status?: 'ok' | 'partial';
  lastAppliedRev?: number;
  fileResults?: FileResult[];
  remainingUploads?: string[];
  idempotent?: boolean;
  error?: string;
}

// =============================================================================
// Vault Info
// =============================================================================

export interface VaultInfo {
  id: string;
  name: string;
  domain: string;
  lastAppliedRev: number;
  createdAt: string;
}

export interface VaultInfoResponse {
  success: boolean;
  vault?: VaultInfo;
  error?: string;
}

// =============================================================================
// Sync V2 API Interface
// =============================================================================

export interface SyncV2Api {
  getVaultInfo(): Promise<VaultInfoResponse>;
  plan(request: PlanRequest): Promise<PlanResponse>;
  commit(request: CommitRequest): Promise<CommitResponse>;
}
