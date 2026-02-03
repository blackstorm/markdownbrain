import { type App, TFile } from "obsidian";
import type { CachedMetadataLike } from "../services";
import { isAssetFile } from "../utils";

export type FileDeleteCallback = (file: TFile) => void;
export type FileRenameCallback = (file: TFile, oldPath: string) => void;
export type AssetEventCallback = (file: TFile, action: "create" | "modify") => void;
export type AssetDeleteCallback = (file: TFile) => void;
export type AssetRenameCallback = (file: TFile, oldPath: string) => void;
export type MarkdownCacheChangedCallback = (
  file: TFile,
  data: string,
  cache: CachedMetadataLike | null,
) => void;
export type MarkdownCreatedCallback = (file: TFile) => void;

export type ResourceEventCallback = AssetEventCallback;
export type ResourceDeleteCallback = AssetDeleteCallback;
export type ResourceRenameCallback = AssetRenameCallback;

export interface EventHandlers {
  onFileDelete: FileDeleteCallback;
  onFileRename: FileRenameCallback;
  onAssetChange: AssetEventCallback;
  onAssetDelete: AssetDeleteCallback;
  onAssetRename: AssetRenameCallback;
  onMarkdownCacheChanged: MarkdownCacheChangedCallback;
  onMarkdownCreated: MarkdownCreatedCallback;
}

export function registerFileEvents(
  app: App,
  handlers: EventHandlers,
  registerEvent: (event: ReturnType<typeof app.vault.on>) => void,
): void {
  registerEvent(
    app.vault.on("create", (file) => {
      if (file instanceof TFile) {
        if (file.extension === "md") {
          handlers.onMarkdownCreated(file);
        } else if (isAssetFile(file)) {
          handlers.onAssetChange(file, "create");
        }
      }
    }),
  );

  registerEvent(
    app.vault.on("modify", (file) => {
      if (file instanceof TFile) {
        if (isAssetFile(file)) {
          handlers.onAssetChange(file, "modify");
        }
      }
    }),
  );

  registerEvent(
    app.metadataCache.on("changed", (file, data, cache) => {
      if (file instanceof TFile && file.extension === "md") {
        handlers.onMarkdownCacheChanged(
          file,
          String(data ?? ""),
          (cache ?? null) as CachedMetadataLike | null,
        );
      }
    }),
  );

  registerEvent(
    app.vault.on("delete", (file) => {
      if (file instanceof TFile) {
        if (file.extension === "md") {
          handlers.onFileDelete(file);
        } else if (isAssetFile(file)) {
          handlers.onAssetDelete(file);
        }
      }
    }),
  );

  registerEvent(
    app.vault.on("rename", (file, oldPath) => {
      if (file instanceof TFile) {
        if (file.extension === "md") {
          handlers.onFileRename(file, oldPath);
        } else if (isAssetFile(file)) {
          handlers.onAssetRename(file, oldPath);
        }
      }
    }),
  );
}
