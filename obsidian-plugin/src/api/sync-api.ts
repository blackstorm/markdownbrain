/**
 * Sync API Client
 * 
 * HTTP API client for MarkdownBrain server.
 * Uses HttpClient interface for testability (can mock Obsidian's requestUrl).
 */

import type {
  SyncConfig,
  SyncData,
  AssetSyncData,
  TestConnectionResult,
  SyncResult,
  FullSyncResponse,
  FullSyncRequest,
  SyncApi,
} from '../domain/types';

// =============================================================================
// HTTP Client Interface (for dependency injection)
// =============================================================================

export interface HttpResponse {
  status: number;
  json: unknown;
  text: string;
}

export interface HttpRequest {
  url: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  headers: Record<string, string>;
  body?: string;
}

export interface HttpClient {
  request(req: HttpRequest): Promise<HttpResponse>;
}

// =============================================================================
// Sync API Client Implementation
// =============================================================================

export class SyncApiClient implements SyncApi {
  private config: SyncConfig;
  private http: HttpClient;

  constructor(config: SyncConfig, httpClient: HttpClient) {
    this.config = config;
    this.http = httpClient;
  }

  updateConfig(config: SyncConfig): void {
    this.config = config;
  }

  async testConnection(timeout: number = 30000): Promise<TestConnectionResult> {
    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/vault/info`,
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${this.config.syncKey}`
        }
      });

      if (response.status === 200) {
        const data = response.json as { vault: TestConnectionResult['vaultInfo'] };
        return {
          success: true,
          vaultInfo: data.vault
        };
      } else {
        return {
          success: false,
          error: `HTTP ${response.status}: ${response.text || 'Unknown error'}`
        };
      }
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'name' in error && error.name === 'AbortError') {
        return {
          success: false,
          error: `连接超时（${timeout / 1000}秒）`
        };
      }
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  async syncNote(data: SyncData): Promise<SyncResult> {
    console.log('[MarkdownBrain] Starting sync:', {
      path: data.path,
      action: data.action,
      hasContent: !!data.content,
      contentLength: data.content?.length,
      serverUrl: this.config.serverUrl,
      hasSyncKey: !!this.config.syncKey
    });

    try {
      const requestBody = JSON.stringify(data);
      console.log('[MarkdownBrain] Request body size:', requestBody.length, 'bytes');

      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/sync`,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.config.syncKey}`
        },
        body: requestBody
      });

      console.log('[MarkdownBrain] Response:', {
        status: response.status,
        bodyPreview: response.text?.substring(0, 200)
      });

      if (response.status === 200) {
        console.log('[MarkdownBrain] ✓ Sync successful:', data.path);
        return { success: true };
      } else {
        const errorMsg = `HTTP ${response.status}: ${response.text || 'Sync failed'}`;
        console.error('[MarkdownBrain] ✗ Sync failed:', {
          path: data.path,
          status: response.status,
          error: errorMsg,
          responseBody: response.text
        });
        return {
          success: false,
          error: errorMsg
        };
      }
    } catch (error) {
      console.error('[MarkdownBrain] ✗ Sync exception:', {
        path: data.path,
        error: error,
        errorMessage: error instanceof Error ? error.message : 'Unknown error',
        errorStack: error instanceof Error ? error.stack : undefined
      });
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      };
    }
  }

  async fullSync(clientIds: string[]): Promise<FullSyncResponse> {
    console.log('[MarkdownBrain] Starting full sync...', {
      clientNoteCount: clientIds.length
    });

    try {
      const requestBody: FullSyncRequest = { clientIds };
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/sync/full`,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.config.syncKey}`
        },
        body: JSON.stringify(requestBody)
      });

      // Backward compatibility: old servers don't support full-sync (404)
      if (response.status === 404) {
        console.warn('[MarkdownBrain] Full sync not supported by server (404), skipping orphan cleanup');
        return {
          success: true,
          data: {
            'vault-id': '',
            action: 'full-sync',
            'client-notes': clientIds.length,
            'deleted-count': 0,
            'remaining-notes': clientIds.length
          }
        };
      }

      if (response.status === 200) {
        const data = response.json as FullSyncResponse['data'];
        console.log('[MarkdownBrain] ✓ Full sync successful:', {
          deletedCount: data?.['deleted-count'],
          remainingNotes: data?.['remaining-notes']
        });
        return {
          success: true,
          data: data
        };
      } else {
        const errorMsg = `HTTP ${response.status}: ${response.text || 'Full sync failed'}`;
        console.error('[MarkdownBrain] ✗ Full sync failed:', {
          status: response.status,
          error: errorMsg
        });
        return {
          success: false,
          error: errorMsg
        };
      }
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : 'Unknown error';
      console.error('[MarkdownBrain] ✗ Full sync exception:', {
        error: errorMsg
      });
      return {
        success: false,
        error: errorMsg
      };
    }
  }

  async syncAsset(data: AssetSyncData): Promise<SyncResult> {
    console.log('[MarkdownBrain] Starting asset sync:', {
      path: data.path,
      clientId: data.clientId,
      action: data.action,
      contentType: data.contentType,
      size: data.size
    });

    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/assets/sync`,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.config.syncKey}`
        },
        body: JSON.stringify(data)
      });

      if (response.status === 200) {
        console.log('[MarkdownBrain] ✓ Asset sync successful:', data.path);
        return { success: true };
      } else {
        const errorMsg = `HTTP ${response.status}: ${response.text || 'Asset sync failed'}`;
        console.error('[MarkdownBrain] ✗ Asset sync failed:', {
          path: data.path,
          status: response.status,
          error: errorMsg
        });
        return { success: false, error: errorMsg };
      }
    } catch (error) {
      console.error('[MarkdownBrain] ✗ Asset sync exception:', {
        path: data.path,
        error: error instanceof Error ? error.message : 'Unknown error'
      });
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
