import { type App, TFile } from "obsidian";
import { isAssetFile } from "../utils";

export type FileEventCallback = (file: TFile, action: "create" | "modify") => void;
export type FileDeleteCallback = (file: TFile) => void;
export type FileRenameCallback = (file: TFile, oldPath: string) => void;
export type AssetEventCallback = (file: TFile, action: "create" | "modify") => void;
export type AssetDeleteCallback = (file: TFile) => void;
export type AssetRenameCallback = (file: TFile, oldPath: string) => void;
export type MetadataResolvedCallback = () => void;

export type ResourceEventCallback = AssetEventCallback;
export type ResourceDeleteCallback = AssetDeleteCallback;
export type ResourceRenameCallback = AssetRenameCallback;

export interface EventHandlers {
  onFileChange: FileEventCallback;
  onFileDelete: FileDeleteCallback;
  onFileRename: FileRenameCallback;
  onAssetChange: AssetEventCallback;
  onAssetDelete: AssetDeleteCallback;
  onAssetRename: AssetRenameCallback;
  onMetadataResolved: MetadataResolvedCallback;
}

export interface PendingSyncsManager {
  add(path: string): void;
  clear(): void;
  forEach(callback: (path: string) => void): void;
}

export function registerFileEvents(
  app: App,
  handlers: EventHandlers,
  pendingSyncs: PendingSyncsManager,
  registerEvent: (event: ReturnType<typeof app.vault.on>) => void,
): void {
  registerEvent(
    app.vault.on("create", (file) => {
      if (file instanceof TFile) {
        if (file.extension === "md") {
          handlers.onFileChange(file, "create");
        } else if (isAssetFile(file)) {
          handlers.onAssetChange(file, "create");
        }
      }
    }),
  );

  registerEvent(
    app.vault.on("modify", (file) => {
      if (file instanceof TFile) {
        if (file.extension === "md") {
          pendingSyncs.add(file.path);
        } else if (isAssetFile(file)) {
          handlers.onAssetChange(file, "modify");
        }
      }
    }),
  );

  registerEvent(
    app.metadataCache.on("resolved", () => {
      pendingSyncs.forEach((filePath) => {
        const file = app.vault.getAbstractFileByPath(filePath);
        if (file instanceof TFile) {
          handlers.onFileChange(file, "modify");
        }
      });
      pendingSyncs.clear();
      handlers.onMetadataResolved();
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
