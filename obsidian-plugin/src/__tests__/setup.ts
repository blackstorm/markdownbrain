import { mock } from 'bun:test';

const mockParseYaml = (yaml: string): any => {
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
};

const mockStringifyYaml = (obj: any): string => {
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
};

class MockTFile {
  path: string;
  basename: string;
  extension: string;
  stat: { mtime: number; ctime: number; size: number };

  constructor(path: string) {
    this.path = path;
    this.basename = path.replace(/\.[^/.]+$/, '');
    this.extension = 'md';
    this.stat = { mtime: Date.now(), ctime: Date.now(), size: 100 };
  }
}

class MockMetadataCache {
  private cache: Map<string, any> = new Map();
  getFileCache(file: MockTFile): any { return this.cache.get(file.path); }
  setFileCache(path: string, cache: any): void { this.cache.set(path, cache); }
}

class MockVault {
  private files: Map<string, string> = new Map();
  async read(file: MockTFile): Promise<string> { return this.files.get(file.path) || ''; }
  setFileContent(path: string, content: string): void { this.files.set(path, content); }
}

class MockFileManager {
  private vault: MockVault;
  constructor(vault: MockVault) { this.vault = vault; }
  
  async processFrontMatter(file: MockTFile, fn: (frontmatter: any) => void): Promise<void> {
    const content = await this.vault.read(file);
    const frontmatterMatch = content.match(/^---\n([\s\S]*?)\n---/);
    
    let frontmatter: Record<string, any> = {};
    let body = content;
    
    if (frontmatterMatch) {
      frontmatter = mockParseYaml(frontmatterMatch[1]) || {};
      body = content.slice(frontmatterMatch[0].length);
    }
    
    fn(frontmatter);
    
    const newFrontmatterStr = mockStringifyYaml(frontmatter);
    const newContent = `---\n${newFrontmatterStr}---${body}`;
    this.vault.setFileContent(file.path, newContent);
  }
}

class MockApp {
  vault: MockVault;
  metadataCache: MockMetadataCache;
  fileManager: MockFileManager;

  constructor() {
    this.vault = new MockVault();
    this.metadataCache = new MockMetadataCache();
    this.fileManager = new MockFileManager(this.vault);
  }
}

mock.module('obsidian', () => ({
  TFile: MockTFile,
  App: MockApp,
  parseYaml: mockParseYaml,
  stringifyYaml: mockStringifyYaml,
}));

export { MockTFile, MockApp, MockVault, MockMetadataCache, MockFileManager, mockParseYaml, mockStringifyYaml };
