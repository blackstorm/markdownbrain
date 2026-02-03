import { TFile, TFolder } from "obsidian";
import { describe, expect, test } from "vitest";
import { ObsidianMetadataAdapter, ObsidianVaultAdapter } from "./obsidian-vault";

const createVault = () => {
  const files = new Map<string, { content: string; binary: ArrayBuffer; file: TFile }>();

  const addFile = (path: string, content: string, binary: ArrayBuffer) => {
    const file = new TFile(path);
    files.set(path, { content, binary, file });
    return file;
  };

  return {
    addFile,
    vault: {
      getAbstractFileByPath: (path: string) => files.get(path)?.file ?? null,
      getMarkdownFiles: () => Array.from(files.values()).map((entry) => entry.file),
      read: async (file: TFile) => files.get(file.path)?.content ?? "",
      readBinary: async (file: TFile) => files.get(file.path)?.binary ?? new ArrayBuffer(0),
    },
  };
};

describe("ObsidianVaultAdapter", () => {
  test("maps markdown files with stats", () => {
    const { vault, addFile } = createVault();
    const file = addFile("notes/a.md", "# A", new ArrayBuffer(1));
    file.stat = { mtime: 10, ctime: 5, size: 42 };

    const adapter = new ObsidianVaultAdapter({ vault } as never);
    const result = adapter.getMarkdownFiles();

    expect(result).toEqual([
      {
        path: "notes/a.md",
        extension: "md",
        stat: { mtime: 10, ctime: 5, size: 42 },
      },
    ]);
  });

  test("reads text and binary content", async () => {
    const { vault, addFile } = createVault();
    const binary = new Uint8Array([1, 2, 3]).buffer;
    addFile("notes/b.md", "hello", binary);

    const adapter = new ObsidianVaultAdapter({ vault } as never);
    const fileInfo = {
      path: "notes/b.md",
      extension: "md",
      stat: { mtime: 1, ctime: 1, size: 5 },
    };

    await expect(adapter.readText(fileInfo)).resolves.toBe("hello");
    await expect(adapter.readBinary(fileInfo)).resolves.toBe(binary);
  });

  test("throws when file missing", async () => {
    const { vault } = createVault();
    const adapter = new ObsidianVaultAdapter({ vault } as never);
    const fileInfo = {
      path: "missing.md",
      extension: "md",
      stat: { mtime: 1, ctime: 1, size: 0 },
    };

    await expect(adapter.readText(fileInfo)).rejects.toThrow("File not found: missing.md");
    await expect(adapter.readBinary(fileInfo)).rejects.toThrow("File not found: missing.md");
  });

  test("returns stat details", () => {
    const { vault } = createVault();
    const adapter = new ObsidianVaultAdapter({ vault } as never);
    const fileInfo = {
      path: "notes/c.md",
      extension: "md",
      stat: { mtime: 11, ctime: 8, size: 12 },
    };

    expect(adapter.stat(fileInfo)).toEqual({ mtime: 11, size: 12 });
  });
});

describe("ObsidianMetadataAdapter", () => {
  test("returns cached metadata for files", () => {
    const cache = new Map<string, unknown>();
    const vault = {
      getAbstractFileByPath: (path: string) => new TFile(path),
    };
    const metadataCache = {
      getFileCache: (file: TFile) => cache.get(file.path),
    };
    cache.set("notes/meta.md", {
      tags: [{ tag: "#tag" }],
      headings: [
        { heading: "Title", level: 1, position: { start: { line: 0, col: 0, offset: 0 } } },
      ],
      frontmatter: { title: "Note" },
    });

    const adapter = new ObsidianMetadataAdapter({ vault, metadataCache } as never);
    const result = adapter.getFileCache("notes/meta.md");

    expect(result).toEqual({
      tags: [{ tag: "#tag" }],
      headings: [
        { heading: "Title", level: 1, position: { start: { line: 0, col: 0, offset: 0 } } },
      ],
      frontmatter: { title: "Note" },
    });
  });

  test("returns null when no cache or not a file", () => {
    const vault = {
      getAbstractFileByPath: (_path: string) => new TFolder("notes", "notes"),
    };
    const metadataCache = {
      getFileCache: (_file: TFile) => null,
    };
    const adapter = new ObsidianMetadataAdapter({ vault, metadataCache } as never);

    expect(adapter.getFileCache("notes")).toBeNull();
  });
});
