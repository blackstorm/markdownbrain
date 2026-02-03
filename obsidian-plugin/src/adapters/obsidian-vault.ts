import { type App, TFile } from "obsidian";
import type { CachedMetadata, FileInfo, MetadataPort, VaultPort } from "../domain/types";

export class ObsidianVaultAdapter implements VaultPort {
  constructor(private app: App) {}

  getMarkdownFiles(): FileInfo[] {
    return this.app.vault.getMarkdownFiles().map((file) => ({
      path: file.path,
      extension: file.extension,
      stat: {
        mtime: file.stat.mtime,
        ctime: file.stat.ctime,
        size: file.stat.size,
      },
    }));
  }

  async readText(file: FileInfo): Promise<string> {
    const tfile = this.app.vault.getAbstractFileByPath(file.path);
    if (!(tfile instanceof TFile)) {
      throw new Error(`File not found: ${file.path}`);
    }
    return this.app.vault.read(tfile);
  }

  async readBinary(file: FileInfo): Promise<ArrayBuffer> {
    const tfile = this.app.vault.getAbstractFileByPath(file.path);
    if (!(tfile instanceof TFile)) {
      throw new Error(`File not found: ${file.path}`);
    }
    return this.app.vault.readBinary(tfile);
  }

  stat(file: FileInfo): { mtime: number; size: number } {
    return {
      mtime: file.stat.mtime,
      size: file.stat.size,
    };
  }
}

export class ObsidianMetadataAdapter implements MetadataPort {
  constructor(private app: App) {}

  getFileCache(path: string): CachedMetadata | null {
    const file = this.app.vault.getAbstractFileByPath(path);
    if (!(file instanceof TFile)) {
      return null;
    }
    const cache = this.app.metadataCache.getFileCache(file);
    if (!cache) {
      return null;
    }
    return {
      tags: cache.tags,
      headings: cache.headings,
      frontmatter: cache.frontmatter,
    };
  }
}
