/**
 * Snapshot-based sync API client.
 */

import type { SyncConfig } from "../domain/types";
import type { HttpClient } from "./http-client";

export interface SyncSnapshotEntry {
  id: string;
  hash: string;
}

export interface SyncChangesRequest {
  notes: SyncSnapshotEntry[];
  assets: SyncSnapshotEntry[];
}

export interface SyncChangesResponse {
  success: boolean;
  need_upsert?: {
    notes: SyncSnapshotEntry[];
    assets: SyncSnapshotEntry[];
  };
  deleted_on_server?: {
    notes: SyncSnapshotEntry[];
    assets: SyncSnapshotEntry[];
  };
  error?: string;
}

export interface SyncNoteRequest {
  path: string;
  content: string;
  hash: string;
  metadata?: Record<string, unknown>;
  assets: Array<{ id: string; hash: string }>;
  linked_notes: Array<{ id: string; hash: string }>;
}

export interface SyncAssetRequest {
  path: string;
  contentType: string;
  size: number;
  hash: string;
  content: string;
}

export interface VaultInfoResponse {
  success: boolean;
  vault?: {
    id: string;
    name: string;
    domain?: string;
    createdAt?: string;
  };
  error?: string;
}

export class SyncApiClient {
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
        method: "GET",
        headers: {
          Authorization: `Bearer ${this.config.publishKey}`,
        },
      });

      if (response.status === 200) {
        const data = response.json as { vault: VaultInfoResponse["vault"] };
        return { success: true, vault: data.vault };
      }
      return {
        success: false,
        error: `HTTP ${response.status}: ${response.text}`,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      };
    }
  }

  async syncChanges(request: SyncChangesRequest): Promise<SyncChangesResponse> {
    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/sync/changes`,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.publishKey}`,
        },
        body: JSON.stringify(request),
      });

      if (response.status === 200) {
        const data = response.json as Omit<SyncChangesResponse, "success">;
        return { success: true, ...data };
      }

      return {
        success: false,
        error: `HTTP ${response.status}: ${response.text}`,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      };
    }
  }

  async syncNote(
    noteId: string,
    request: SyncNoteRequest,
  ): Promise<{
    success: boolean;
    error?: string;
    need_upload_assets?: Array<{ id: string; hash: string }>;
    need_upload_notes?: Array<{ id: string; hash: string }>;
  }> {
    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/sync/notes/${encodeURIComponent(noteId)}`,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.publishKey}`,
        },
        body: JSON.stringify(request),
      });

      if (response.status === 200) {
        const data = response.json as {
          need_upload_assets?: Array<{ id: string; hash: string }>;
          need_upload_notes?: Array<{ id: string; hash: string }>;
        };
        return {
          success: true,
          need_upload_assets: data.need_upload_assets,
          need_upload_notes: data.need_upload_notes,
        };
      }
      return {
        success: false,
        error: `HTTP ${response.status}: ${response.text}`,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      };
    }
  }

  async syncAsset(
    assetId: string,
    request: SyncAssetRequest,
  ): Promise<{ success: boolean; error?: string }> {
    try {
      const response = await this.http.request({
        url: `${this.config.serverUrl}/obsidian/sync/assets/${encodeURIComponent(assetId)}`,
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.publishKey}`,
        },
        body: JSON.stringify(request),
      });

      if (response.status === 200) {
        return { success: true };
      }
      return {
        success: false,
        error: `HTTP ${response.status}: ${response.text}`,
      };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : "Unknown error",
      };
    }
  }
}
