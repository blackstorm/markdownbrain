/**
 * Sync V2 API Client
 * 
 * Implements the new plan/commit sync protocol.
 * Uses HttpClient interface for testability.
 */

import type { HttpClient } from './http-client';
import type {
  SyncV2Api,
  PlanRequest,
  PlanResponse,
  CommitRequest,
  CommitResponse,
  VaultInfoResponse,
} from './sync-v2-types';
import type { SyncConfig } from '../domain/types';

export class SyncV2Client implements SyncV2Api {
  private config: SyncConfig;
  private http: HttpClient;

  constructor(config: SyncConfig, httpClient: HttpClient) {
    this.config = config;
    this.http = httpClient;
  }

  updateConfig(config: SyncConfig): void {
    this.config = config;
  }

  async getVaultInfo(): Promise<VaultInfoResponse> {
    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/vault/info`,
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${this.config.syncKey}`
        }
      });

      if (response.status === 200) {
        const data = response.json as { vault: VaultInfoResponse['vault'] };
        return { success: true, vault: data.vault };
      }
      return { success: false, error: `HTTP ${response.status}: ${response.text}` };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  async plan(request: PlanRequest): Promise<PlanResponse> {
    console.log('[MarkdownBrain] Sync plan request:', {
      mode: request.mode,
      baseRev: request.baseRev,
      opsCount: request.ops?.length,
      manifestCount: request.manifest?.length
    });

    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/v1/sync/plan`,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.config.syncKey}`
        },
        body: JSON.stringify(request)
      });

      if (response.status === 200) {
        const data = response.json as Omit<PlanResponse, 'success'>;
        console.log('[MarkdownBrain] Plan success:', {
          syncToken: data.syncToken?.substring(0, 8) + '...',
          needUpload: data.needUpload?.length,
          alreadyHave: data.alreadyHave?.length
        });
        return { success: true, ...data };
      }

      if (response.status === 409) {
        const data = response.json as { serverLastAppliedRev: number };
        console.warn('[MarkdownBrain] Cursor mismatch:', data);
        return {
          success: false,
          error: 'Cursor mismatch',
          serverLastAppliedRev: data.serverLastAppliedRev
        };
      }

      return { success: false, error: `HTTP ${response.status}: ${response.text}` };
    } catch (error) {
      console.error('[MarkdownBrain] Plan error:', error);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  async commit(request: CommitRequest): Promise<CommitResponse> {
    console.log('[MarkdownBrain] Sync commit request:', {
      syncToken: request.syncToken.substring(0, 8) + '...',
      filesCount: request.files?.length,
      finalize: request.finalize
    });

    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/v1/sync/commit`,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.config.syncKey}`
        },
        body: JSON.stringify(request)
      });

      if (response.status === 200) {
        const data = response.json as Omit<CommitResponse, 'success'>;
        console.log('[MarkdownBrain] Commit success:', {
          status: data.status,
          lastAppliedRev: data.lastAppliedRev,
          fileResults: data.fileResults?.length
        });
        return { success: true, ...data };
      }

      return { success: false, error: `HTTP ${response.status}: ${response.text}` };
    } catch (error) {
      console.error('[MarkdownBrain] Commit error:', error);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  destroy(): void {
    // Cleanup if needed
  }
}
