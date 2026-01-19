import { TFile, TFolder } from "obsidian";
import { describe, expect, test, vi } from "vitest";
import { registerFileEvents } from "./events";

describe("registerFileEvents", () => {
  const createHarness = () => {
    const vaultCallbacks = new Map<string, (file: unknown, oldPath?: string) => void>();
    const metadataCallbacks = new Map<string, () => void>();
    const files = new Map<string, TFile>();

    const app = {
      vault: {
        on: (name: string, callback: (file: unknown, oldPath?: string) => void) => {
          vaultCallbacks.set(name, callback);
          return { name, callback };
        },
        getAbstractFileByPath: (path: string) => files.get(path) ?? null,
      },
      metadataCache: {
        on: (name: string, callback: () => void) => {
          metadataCallbacks.set(name, callback);
          return { name, callback };
        },
      },
    };

    const handlers = {
      onFileChange: vi.fn(),
      onFileDelete: vi.fn(),
      onFileRename: vi.fn(),
      onAssetChange: vi.fn(),
      onAssetDelete: vi.fn(),
      onAssetRename: vi.fn(),
      onMetadataResolved: vi.fn(),
    };

    const pendingSyncs = new Set<string>();
    const pendingManager = {
      add: (path: string) => pendingSyncs.add(path),
      clear: () => pendingSyncs.clear(),
      forEach: (callback: (path: string) => void) => pendingSyncs.forEach(callback),
    };

    const registeredEvents: Array<{
      name: string;
      callback: (file: unknown, oldPath?: string) => void;
    }> = [];
    const registerEvent = (event: {
      name: string;
      callback: (file: unknown, oldPath?: string) => void;
    }) => {
      registeredEvents.push(event);
    };

    return {
      app,
      handlers,
      pendingManager,
      pendingSyncs,
      vaultCallbacks,
      metadataCallbacks,
      files,
      registerEvent,
    };
  };

  test("registers and routes vault events", () => {
    const harness = createHarness();
    registerFileEvents(
      harness.app as never,
      harness.handlers,
      harness.pendingManager,
      harness.registerEvent,
    );

    const note = new TFile("note.md");
    const asset = new TFile("assets/image.png", "image", "png");
    harness.files.set(note.path, note);
    harness.files.set(asset.path, asset);

    const createHandler = harness.vaultCallbacks.get("create");
    createHandler?.(note);
    createHandler?.(asset);

    expect(harness.handlers.onFileChange).toHaveBeenCalledWith(note, "create");
    expect(harness.handlers.onAssetChange).toHaveBeenCalledWith(asset, "create");

    const modifyHandler = harness.vaultCallbacks.get("modify");
    modifyHandler?.(note);
    modifyHandler?.(asset);

    expect(harness.pendingSyncs.has(note.path)).toBe(true);
    expect(harness.handlers.onAssetChange).toHaveBeenCalledWith(asset, "modify");

    const deleteHandler = harness.vaultCallbacks.get("delete");
    deleteHandler?.(note);
    deleteHandler?.(asset);

    expect(harness.handlers.onFileDelete).toHaveBeenCalledWith(note);
    expect(harness.handlers.onAssetDelete).toHaveBeenCalledWith(asset);

    const renameHandler = harness.vaultCallbacks.get("rename");
    renameHandler?.(note, "old.md");
    renameHandler?.(asset, "old.png");

    expect(harness.handlers.onFileRename).toHaveBeenCalledWith(note, "old.md");
    expect(harness.handlers.onAssetRename).toHaveBeenCalledWith(asset, "old.png");
  });

  test("flushes pending markdown changes on metadata resolved", () => {
    const harness = createHarness();
    registerFileEvents(
      harness.app as never,
      harness.handlers,
      harness.pendingManager,
      harness.registerEvent,
    );

    const note = new TFile("notes/todo.md");
    harness.files.set(note.path, note);
    harness.pendingSyncs.add(note.path);

    const resolvedHandler = harness.metadataCallbacks.get("resolved");
    resolvedHandler?.();

    expect(harness.handlers.onFileChange).toHaveBeenCalledWith(note, "modify");
    expect(harness.pendingSyncs.size).toBe(0);
    expect(harness.handlers.onMetadataResolved).toHaveBeenCalled();
  });

  test("ignores non-file events", () => {
    const harness = createHarness();
    registerFileEvents(
      harness.app as never,
      harness.handlers,
      harness.pendingManager,
      harness.registerEvent,
    );

    const folder = new TFolder("notes", "notes");
    const createHandler = harness.vaultCallbacks.get("create");
    createHandler?.(folder);

    expect(harness.handlers.onFileChange).not.toHaveBeenCalled();
    expect(harness.handlers.onAssetChange).not.toHaveBeenCalled();
  });
});
