import { describe, test, expect, beforeEach, mock } from 'bun:test';
import { SyncApiClient, type HttpClient, type HttpResponse } from '../api/sync-api';
import type { SyncData, AssetSyncData } from '../domain/types';

describe('SyncApiClient', () => {
  let mockHttpClient: HttpClient;
  let api: SyncApiClient;

  const config = {
    serverUrl: 'https://api.example.com',
    syncKey: 'test-sync-key-123'
  };

  beforeEach(() => {
    mockHttpClient = {
      request: mock(() => Promise.resolve({ status: 200, json: {}, text: '' }))
    };
    api = new SyncApiClient(config, mockHttpClient);
  });

  describe('testConnection', () => {
    test('should return success with vault info on 200', async () => {
      const vaultInfo = { id: 'v1', name: 'Test Vault', domain: 'test.com', 'created-at': '2024-01-01' };
      (mockHttpClient.request as any).mockResolvedValue({
        status: 200,
        json: { vault: vaultInfo },
        text: ''
      });

      const result = await api.testConnection();

      expect(result.success).toBe(true);
      expect(result.vaultInfo).toEqual(vaultInfo);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: 'https://api.example.com/obsidian/vault/info',
        method: 'GET',
        headers: { 'Authorization': 'Bearer test-sync-key-123' }
      });
    });

    test('should return error on non-200 status', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 401,
        json: {},
        text: 'Unauthorized'
      });

      const result = await api.testConnection();

      expect(result.success).toBe(false);
      expect(result.error).toContain('401');
    });

    test('should return error on timeout', async () => {
      (mockHttpClient.request as any).mockRejectedValue({ name: 'AbortError' });

      const result = await api.testConnection(1000);

      expect(result.success).toBe(false);
      expect(result.error).toContain('超时');
    });

    test('should return error on network failure', async () => {
      (mockHttpClient.request as any).mockRejectedValue(new Error('Network error'));

      const result = await api.testConnection();

      expect(result.success).toBe(false);
      expect(result.error).toBe('Network error');
    });
  });

  describe('syncNote', () => {
    const syncData: SyncData = {
      path: 'test.md',
      clientId: 'client-123',
      clientType: 'obsidian',
      content: '# Hello',
      hash: 'abc123',
      mtime: '2024-01-01T00:00:00Z',
      action: 'create'
    };

    test('should return success on 200', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 200,
        json: { success: true },
        text: ''
      });

      const result = await api.syncNote(syncData);

      expect(result.success).toBe(true);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: 'https://api.example.com/obsidian/sync',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer test-sync-key-123'
        },
        body: JSON.stringify(syncData)
      });
    });

    test('should return error on non-200 status', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 500,
        json: {},
        text: 'Internal Server Error'
      });

      const result = await api.syncNote(syncData);

      expect(result.success).toBe(false);
      expect(result.error).toContain('500');
    });
  });

  describe('fullSync', () => {
    test('should return success with data on 200', async () => {
      const responseData = {
        'vault-id': 'v1',
        action: 'full-sync',
        'client-notes': 10,
        'deleted-count': 2,
        'remaining-notes': 8
      };
      (mockHttpClient.request as any).mockResolvedValue({
        status: 200,
        json: responseData,
        text: ''
      });

      const result = await api.fullSync(['id1', 'id2', 'id3']);

      expect(result.success).toBe(true);
      expect(result.data).toEqual(responseData);
    });

    test('should handle 404 gracefully (old server)', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 404,
        json: {},
        text: 'Not Found'
      });

      const result = await api.fullSync(['id1', 'id2']);

      expect(result.success).toBe(true);
      expect(result.data?.['deleted-count']).toBe(0);
    });

    test('should return error on other non-200 status', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 500,
        json: {},
        text: 'Internal Server Error'
      });

      const result = await api.fullSync(['id1']);

      expect(result.success).toBe(false);
      expect(result.error).toContain('500');
    });
  });

  describe('syncAsset', () => {
    const assetData: AssetSyncData = {
      path: 'images/test.png',
      clientId: 'asset-client-id-123',
      content: 'base64encodedcontent',
      contentType: 'image/png',
      sha256: 'abc123',
      size: 1024,
      action: 'create'
    };

    test('should return success on 200', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 200,
        json: { success: true },
        text: ''
      });

      const result = await api.syncAsset(assetData);

      expect(result.success).toBe(true);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: 'https://api.example.com/obsidian/assets/sync',
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer test-sync-key-123'
        },
        body: JSON.stringify(assetData)
      });
    });

    test('should return error on non-200 status', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 413,
        json: {},
        text: 'Payload Too Large'
      });

      const result = await api.syncAsset(assetData);

      expect(result.success).toBe(false);
      expect(result.error).toContain('413');
    });
  });

  describe('updateConfig', () => {
    test('should update config and use new values', async () => {
      (mockHttpClient.request as any).mockResolvedValue({
        status: 200,
        json: { vault: {} },
        text: ''
      });

      api.updateConfig({
        serverUrl: 'https://new-api.example.com',
        syncKey: 'new-key'
      });

      await api.testConnection();

      expect(mockHttpClient.request).toHaveBeenCalledWith(
        expect.objectContaining({
          url: 'https://new-api.example.com/obsidian/vault/info',
          headers: { 'Authorization': 'Bearer new-key' }
        })
      );
    });
  });
});
