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

  on(_name: string, _callback: Function): void {}
}

export class FileManager {
  private vault: Vault;

  constructor(vault: Vault) {
    this.vault = vault;
  }

  async processFrontMatter(
    file: TFile,
    fn: (frontmatter: any) => void
  ): Promise<void> {
    const content = await this.vault.read(file);
    const frontmatterMatch = content.match(/^---\n([\s\S]*?)\n---/);
    
    let frontmatter: Record<string, any> = {};
    let body = content;
    
    if (frontmatterMatch) {
      frontmatter = parseYaml(frontmatterMatch[1]) || {};
      body = content.slice(frontmatterMatch[0].length);
    }
    
    fn(frontmatter);
    
    const newFrontmatterStr = stringifyYaml(frontmatter);
    const newContent = `---\n${newFrontmatterStr}---${body}`;
    this.vault.setFileContent(file.path, newContent);
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

  on(_name: string, _callback: Function): void {}

  setFileContent(path: string, content: string): void {
    this.files.set(path, content);
  }
}

export class App {
  vault: Vault;
  metadataCache: MetadataCache;
  fileManager: FileManager;

  constructor() {
    this.vault = new Vault();
    this.metadataCache = new MetadataCache();
    this.fileManager = new FileManager(this.vault);
  }
}

export class PluginSettingTab {
  constructor(_app: App, _plugin: any) {}
  display(): void {}
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

  async loadSettings(): Promise<void> {}
  async saveSettings(): Promise<void> {}
}

export function notice(message: string): void {
  console.log(`[Notice] ${message}`);
}

export class Notice {
  constructor(message: string) {
    console.log(`[Notice] ${message}`);
  }
}

export function parseYaml(yaml: string): any {
  const result: Record<string, any> = {};
  if (!yaml || !yaml.trim()) return result;
  
  const lines = yaml.split('\n');
  let currentKey: string | null = null;
  let currentArray: string[] | null = null;
  
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    
    if (trimmed.startsWith('- ')) {
      if (currentKey && currentArray) {
        currentArray.push(trimmed.slice(2));
      }
      continue;
    }
    
    if (currentKey && currentArray) {
      result[currentKey] = currentArray;
      currentArray = null;
    }
    
    const colonIndex = trimmed.indexOf(':');
    if (colonIndex > 0) {
      const key = trimmed.slice(0, colonIndex).trim();
      const value = trimmed.slice(colonIndex + 1).trim();
      currentKey = key;
      
      if (value === '') {
        currentArray = [];
      } else {
        result[key] = value.replace(/^["']|["']$/g, '');
      }
    }
  }
  
  if (currentKey && currentArray) {
    result[currentKey] = currentArray;
  }
  
  return result;
}

export function stringifyYaml(obj: any): string {
  const lines: string[] = [];
  
  for (const [key, value] of Object.entries(obj)) {
    if (Array.isArray(value)) {
      lines.push(`${key}:`);
      for (const item of value) {
        lines.push(`  - ${item}`);
      }
    } else if (typeof value === 'string' && value.includes(':')) {
      lines.push(`${key}: "${value}"`);
    } else {
      lines.push(`${key}: ${value}`);
    }
  }
  
  return lines.join('\n') + '\n';
}
