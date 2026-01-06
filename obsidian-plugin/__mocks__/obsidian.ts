// Mock for Obsidian API
// This provides mock implementations for testing without actual Obsidian environment

export class TFile {
  path: string;
  basename: string;
  extension: string;
  stat: { mtime: number; ctime: number; size: number };

  constructor(
    path: string,
    basename?: string,
    extension: string = 'md'
  ) {
    this.path = path;
    this.basename = basename || path.replace(/\.[^/.]+$/, '');
    this.extension = extension;
    this.stat = {
      mtime: Date.now(),
      ctime: Date.now(),
      size: 100
    };
  }
}

export class TFolder {
  path: string;
  name: string;
  children: (TFile | TFolder)[];

  constructor(path: string, name: string) {
    this.path = path;
    this.name = name;
    this.children = [];
  }
}

export class MetadataCache {
  private cache: Map<string, any> = new Map();

  getFileCache(file: TFile): any {
    return this.cache.get(file.path);
  }

  setFileCache(path: string, cache: any): void {
    this.cache.set(path, cache);
  }

  on(name: string, callback: Function): void {
    // Mock event handler
  }
}

export class Vault {
  private files: Map<string, string> = new Map();

  async read(file: TFile): Promise<string> {
    return this.files.get(file.path) || '';
  }

  async process(file: TFile, fn: (data: string) => string): Promise<void> {
    const currentContent = this.files.get(file.path) || '';
    const newContent = fn(currentContent);
    this.files.set(file.path, newContent);
    return Promise.resolve();
  }

  getAbstractFileByPath(path: string): TFile | TFolder | null {
    if (this.files.has(path)) {
      return new TFile(path);
    }
    return null;
  }

  getMarkdownFiles(): TFile[] {
    return Array.from(this.files.keys())
      .filter(path => path.endsWith('.md'))
      .map(path => new TFile(path));
  }

  on(name: string, callback: Function): void {
    // Mock event handler
  }

  // Test helpers
  setFileContent(path: string, content: string): void {
    this.files.set(path, content);
  }
}

export class App {
  vault: Vault;
  metadataCache: MetadataCache;

  constructor() {
    this.vault = new Vault();
    this.metadataCache = new MetadataCache();
  }
}

export class PluginSettingTab {
  constructor(app: App, plugin: any) {
    // Mock constructor
  }

  display(): void {
    // Mock display method
  }
}

export interface PluginManifest {
  id: string;
  name: string;
  version: string;
  minAppVersion: string;
  description: string;
  author: string;
  isDesktopOnly: boolean;
}

export class Plugin {
  app: App;
  manifest: PluginManifest;

  constructor(app: App, manifest: PluginManifest) {
    this.app = app;
    this.manifest = manifest;
  }

  async loadSettings(): Promise<void> {
    // Mock load settings
  }

  async saveSettings(): Promise<void> {
    // Mock save settings
  }
}

export function notice(message: string): void {
  console.log(`[Notice] ${message}`);
}

export class Notice {
  constructor(message: string) {
    console.log(`[Notice] ${message}`);
  }
}
