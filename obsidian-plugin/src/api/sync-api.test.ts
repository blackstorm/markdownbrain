import { beforeEach, describe, expect, test, vi } from "vitest";
import type { HttpClient } from "./http-client";
import { SyncApiClient } from "./sync-api";

describe("SyncApiClient", () => {
  let mockHttpClient: HttpClient;
  let requestMock: ReturnType<typeof vi.fn>;
  let api: SyncApiClient;

  const config = {
    serverUrl: "https://api.example.com",
    syncKey: "test-sync-key-123",
  };

  beforeEach(() => {
    requestMock = vi.fn<ReturnType<HttpClient["request"]>, Parameters<HttpClient["request"]>>(() =>
      Promise.resolve({ status: 200, json: {}, text: "" }),
    );
    mockHttpClient = {
      request: requestMock,
    };
    api = new SyncApiClient(config, mockHttpClient);
  });

  describe("getVaultInfo", () => {
    test("returns success with vault info on 200", async () => {
      const vaultInfo = {
        id: "v1",
        name: "Test Vault",
        domain: "test.com",
        createdAt: "2024-01-01",
      };
      requestMock.mockResolvedValue({
        status: 200,
        json: { vault: vaultInfo },
        text: "",
      });

      const result = await api.getVaultInfo();

      expect(result.success).toBe(true);
      expect(result.vault).toEqual(vaultInfo);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: "https://api.example.com/obsidian/vault/info",
        method: "GET",
        headers: { Authorization: "Bearer test-sync-key-123" },
      });
    });
  });

  describe("syncChanges", () => {
    test("returns success on 200", async () => {
      const responseData = {
        need_upsert: { notes: [], assets: [] },
        deleted_on_server: { notes: [], assets: [] },
      };
      requestMock.mockResolvedValue({
        status: 200,
        json: responseData,
        text: "",
      });

      const result = await api.syncChanges({ notes: [], assets: [] });

      expect(result.success).toBe(true);
      expect(result.need_upsert).toEqual(responseData.need_upsert);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: "https://api.example.com/sync/changes",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer test-sync-key-123",
        },
        body: JSON.stringify({ notes: [], assets: [] }),
      });
    });
  });

  describe("syncNote", () => {
    test("returns success on 200", async () => {
      const result = await api.syncNote("note-1", {
        path: "test.md",
        content: "# Hello",
        hash: "hash-1",
        assets: [{ id: "asset-1", hash: "md5-1" }],
        linked_notes: [{ id: "note-2", hash: "hash-2" }],
      });

      expect(result.success).toBe(true);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: "https://api.example.com/sync/notes/note-1",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer test-sync-key-123",
        },
        body: JSON.stringify({
          path: "test.md",
          content: "# Hello",
          hash: "hash-1",
          assets: [{ id: "asset-1", hash: "md5-1" }],
          linked_notes: [{ id: "note-2", hash: "hash-2" }],
        }),
      });
    });
  });

  describe("syncAsset", () => {
    test("returns success on 200", async () => {
      const result = await api.syncAsset("asset-1", {
        path: "images/test.png",
        contentType: "image/png",
        size: 1024,
        hash: "md5-1",
        content: "base64data",
      });

      expect(result.success).toBe(true);
      expect(mockHttpClient.request).toHaveBeenCalledWith({
        url: "https://api.example.com/sync/assets/asset-1",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: "Bearer test-sync-key-123",
        },
        body: JSON.stringify({
          path: "images/test.png",
          contentType: "image/png",
          size: 1024,
          hash: "md5-1",
          content: "base64data",
        }),
      });
    });
  });

  describe("updateConfig", () => {
    test("updates config and uses new values", async () => {
      requestMock.mockResolvedValue({
        status: 200,
        json: { vault: {} },
        text: "",
      });

      api.updateConfig({
        serverUrl: "https://new-api.example.com",
        syncKey: "new-key",
      });

      await api.getVaultInfo();

      expect(mockHttpClient.request).toHaveBeenCalledWith(
        expect.objectContaining({
          url: "https://new-api.example.com/obsidian/vault/info",
          headers: { Authorization: "Bearer new-key" },
        }),
      );
    });
  });
});
