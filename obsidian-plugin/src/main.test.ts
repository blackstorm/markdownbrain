import type { App, PluginManifest } from "obsidian";
import { TFile } from "obsidian";
import { describe, expect, test, vi } from "vitest";
import { DEFAULT_SETTINGS } from "./domain/types";
import MarkdownBrainPlugin from "./main";

const createPlugin = (appOverrides?: Partial<App>) => {
  const app = {
    vault: {},
    metadataCache: {},
    workspace: { getActiveFile: () => null },
    ...appOverrides,
  } as unknown as App;
  const manifest: PluginManifest = {
    id: "markdownbrain",
    name: "MarkdownBrain",
    version: "0.0.0",
    minAppVersion: "0.0.0",
    description: "",
    author: "",
    isDesktopOnly: false,
  };
  const plugin = new MarkdownBrainPlugin(app, manifest);
  plugin.settings = { ...DEFAULT_SETTINGS };
  return plugin;
};

describe("MarkdownBrainPlugin.handleAssetChange", () => {
  test("does nothing when autoSync is disabled", async () => {
    const plugin = createPlugin();
    const file = new TFile("assets/image.png", "image", "png");
    const collectReferenced = vi.fn().mockResolvedValue([file]);
    const syncAssetFile = vi.fn().mockResolvedValue(true);

    const pluginAccess = plugin as unknown as {
      collectReferencedAssetFiles: () => Promise<TFile[]>;
      syncAssetFile: (file: TFile) => Promise<boolean>;
      settings: { autoSync: boolean };
    };
    pluginAccess.collectReferencedAssetFiles = collectReferenced;
    pluginAccess.syncAssetFile = syncAssetFile;
    pluginAccess.settings.autoSync = false;

    await plugin.handleAssetChange(file);

    expect(collectReferenced).not.toHaveBeenCalled();
    expect(syncAssetFile).not.toHaveBeenCalled();
  });

  test("skips assets that are not referenced", async () => {
    const plugin = createPlugin();
    const file = new TFile("assets/image.png", "image", "png");
    const collectReferenced = vi.fn().mockResolvedValue([]);
    const syncAssetFile = vi.fn().mockResolvedValue(true);

    const pluginAccess = plugin as unknown as {
      collectReferencedAssetFiles: () => Promise<TFile[]>;
      syncAssetFile: (file: TFile) => Promise<boolean>;
    };
    pluginAccess.collectReferencedAssetFiles = collectReferenced;
    pluginAccess.syncAssetFile = syncAssetFile;

    await plugin.handleAssetChange(file);

    expect(syncAssetFile).not.toHaveBeenCalled();
  });

  test("syncs when asset is referenced", async () => {
    const plugin = createPlugin();
    const file = new TFile("assets/image.png", "image", "png");
    const collectReferenced = vi.fn().mockResolvedValue([file]);
    const syncAssetFile = vi.fn().mockResolvedValue(true);

    const pluginAccess = plugin as unknown as {
      collectReferencedAssetFiles: () => Promise<TFile[]>;
      syncAssetFile: (file: TFile) => Promise<boolean>;
    };
    pluginAccess.collectReferencedAssetFiles = collectReferenced;
    pluginAccess.syncAssetFile = syncAssetFile;

    await plugin.handleAssetChange(file);

    expect(syncAssetFile).toHaveBeenCalledWith(file);
  });
});

describe("MarkdownBrainPlugin.collectReferencedAssetFiles", () => {
  test("deduplicates referenced assets across notes", async () => {
    const noteContents = new Map<string, string>();
    const assets = new Map<string, TFile>();

    const vault = {
      getMarkdownFiles: () => Array.from(noteContents.keys()).map((path) => new TFile(path)),
      read: async (file: TFile) => noteContents.get(file.path) ?? "",
    };
    const metadataCache = {
      getFirstLinkpathDest: (path: string) => assets.get(path) ?? null,
    };

    noteContents.set("notes/a.md", "![Alt](assets/image.png)");
    noteContents.set("notes/b.md", "![[assets/image.png]]\n![[notes/other.md]]");
    assets.set("assets/image.png", new TFile("assets/image.png", "image", "png"));

    const plugin = createPlugin({ vault, metadataCache });
    const pluginAccess = plugin as unknown as {
      collectReferencedAssetFiles: () => Promise<TFile[]>;
    };

    const referenced = await pluginAccess.collectReferencedAssetFiles();

    expect(referenced).toHaveLength(1);
    expect(referenced[0]?.path).toBe("assets/image.png");
  });
});

describe("MarkdownBrainPlugin.handleFileRename", () => {
  test("renames cache and syncs note", async () => {
    const plugin = createPlugin();
    const file = new TFile("notes/new.md");
    const rename = vi.fn();
    const syncNoteFile = vi.fn().mockResolvedValue(true);

    const pluginAccess = plugin as unknown as {
      clientIdCache: { rename: (oldPath: string, newPath: string) => void };
      syncNoteFile: (file: TFile) => Promise<boolean>;
      settings: { autoSync: boolean };
    };
    pluginAccess.clientIdCache = { rename };
    pluginAccess.syncNoteFile = syncNoteFile;
    pluginAccess.settings.autoSync = true;

    await plugin.handleFileRename(file, "notes/old.md");

    expect(rename).toHaveBeenCalledWith("notes/old.md", "notes/new.md");
    expect(syncNoteFile).toHaveBeenCalledWith(file);
  });
});
