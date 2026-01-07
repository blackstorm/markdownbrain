import { TFile, App } from 'obsidian';
import { isResourceFile } from '../utils';

export type FileEventCallback = (file: TFile, action: 'create' | 'modify') => void;
export type FileDeleteCallback = (file: TFile) => void;
export type FileRenameCallback = (file: TFile, oldPath: string) => void;
export type ResourceEventCallback = (file: TFile, action: 'upsert') => void;
export type ResourceDeleteCallback = (file: TFile) => void;
export type ResourceRenameCallback = (file: TFile, oldPath: string) => void;
export type MetadataResolvedCallback = () => void;

export interface EventHandlers {
    onFileChange: FileEventCallback;
    onFileDelete: FileDeleteCallback;
    onFileRename: FileRenameCallback;
    onResourceChange: ResourceEventCallback;
    onResourceDelete: ResourceDeleteCallback;
    onResourceRename: ResourceRenameCallback;
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
    registerEvent: (event: ReturnType<typeof app.vault.on>) => void
): void {
    registerEvent(
        app.vault.on('create', (file) => {
            if (file instanceof TFile) {
                if (file.extension === 'md') {
                    handlers.onFileChange(file, 'create');
                } else if (isResourceFile(file)) {
                    handlers.onResourceChange(file, 'upsert');
                }
            }
        })
    );

    registerEvent(
        app.vault.on('modify', (file) => {
            if (file instanceof TFile) {
                if (file.extension === 'md') {
                    pendingSyncs.add(file.path);
                } else if (isResourceFile(file)) {
                    handlers.onResourceChange(file, 'upsert');
                }
            }
        })
    );

    registerEvent(
        app.metadataCache.on('resolved', () => {
            pendingSyncs.forEach((filePath) => {
                const file = app.vault.getAbstractFileByPath(filePath);
                if (file instanceof TFile) {
                    handlers.onFileChange(file, 'modify');
                }
            });
            pendingSyncs.clear();
            handlers.onMetadataResolved();
        })
    );

    registerEvent(
        app.vault.on('delete', (file) => {
            if (file instanceof TFile) {
                if (file.extension === 'md') {
                    handlers.onFileDelete(file);
                } else if (isResourceFile(file)) {
                    handlers.onResourceDelete(file);
                }
            }
        })
    );

    registerEvent(
        app.vault.on('rename', (file, oldPath) => {
            if (file instanceof TFile) {
                if (file.extension === 'md') {
                    handlers.onFileRename(file, oldPath);
                } else if (isResourceFile(file)) {
                    handlers.onResourceRename(file, oldPath);
                }
            }
        })
    );
}
