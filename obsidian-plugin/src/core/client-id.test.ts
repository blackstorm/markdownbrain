import type { App, TFile } from "obsidian";
import { beforeEach, describe, expect, test } from "vitest";
import { MockApp, MockTFile } from "../test/setup";
import { ensureClientId, getClientId, getOrCreateClientId } from "./client-id";

describe("getOrCreateClientId", () => {
  let mockApp: MockApp;
  let mockFile: MockTFile;
  let app: App;
  let file: TFile;

  beforeEach(() => {
    mockApp = new MockApp();
    mockFile = new MockTFile("test.md");
    app = mockApp as unknown as App;
    file = mockFile as unknown as TFile;
  });

  describe("File with existing frontmatter mdbrain-id", () => {
    test("should return existing UUID from frontmatter", async () => {
      const content = `---
mdbrain-id: abc-123-def
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBe("abc-123-def");
    });

    test("should return existing UUID with different format", async () => {
      const content = `---
mdbrain-id: 550e8400-e29b-41d4-a716-446655440000
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBe("550e8400-e29b-41d4-a716-446655440000");
    });
  });

  describe("File without frontmatter", () => {
    test("should generate new UUID and write to frontmatter", async () => {
      const content = "# Content without frontmatter";
      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBeDefined();
      expect(fileId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain("mdbrain-id:");
      expect(updatedContent).toContain(fileId);
    });

    test("should preserve existing content when adding frontmatter", async () => {
      const content = "# Hello World\n\nThis is content.";
      mockApp.vault.setFileContent("test.md", content);

      await getOrCreateClientId(file, content, app);

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain("# Hello World");
      expect(updatedContent).toContain("This is content.");
      expect(updatedContent).toContain("mdbrain-id:");
    });
  });

  describe("File with frontmatter but no mdbrain-id", () => {
    test("should add mdbrain-id field to existing frontmatter", async () => {
      const content = `---
title: Test
tags:
  - example
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBeDefined();

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain("title: Test");
      expect(updatedContent).toContain(`mdbrain-id: ${fileId}`);
    });

    test("should add mdbrain-id to frontmatter with arrays", async () => {
      const content = `---
tags:
  - test
  - example
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBeDefined();

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain("tags:");
      expect(updatedContent).toContain(`mdbrain-id: ${fileId}`);
    });
  });

  describe("Idempotency", () => {
    test("should return same UUID on multiple calls", async () => {
      const content = "# Content";
      mockApp.vault.setFileContent("test.md", content);

      const id1 = await getOrCreateClientId(file, content, app);
      const id2 = await getOrCreateClientId(file, content, app);

      expect(id1).toBe(id2);
    });

    test("should not add multiple mdbrain-id fields on repeated calls", async () => {
      const content = "# Content";
      mockApp.vault.setFileContent("test.md", content);

      await getOrCreateClientId(file, content, app);
      await getOrCreateClientId(file, content, app);

      const updatedContent = await mockApp.vault.read(mockFile);
      const matches = updatedContent.match(/mdbrain-id:/g);
      expect(matches?.length).toBe(1);
    });
  });

  describe("Rename scenarios", () => {
    test("should preserve UUID after rename (same content, different path)", async () => {
      const oldFile = new MockTFile("old.md");
      const newFile = new MockTFile("new.md");
      const content = `---
mdbrain-id: preserved-uuid
---
# Content`;

      mockApp.vault.setFileContent("old.md", content);
      mockApp.vault.setFileContent("new.md", content);

      const oldId = await getOrCreateClientId(oldFile as unknown as TFile, content, app);
      const newId = await getOrCreateClientId(newFile as unknown as TFile, content, app);

      expect(oldId).toBe("preserved-uuid");
      expect(newId).toBe("preserved-uuid");
      expect(oldId).toBe(newId);
    });
  });

  describe("Edge cases", () => {
    test("should handle empty files", async () => {
      const content = "";
      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBeDefined();
      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain("mdbrain-id:");
    });

    test("should handle mdbrain-id field with extra spaces", async () => {
      const content = `---
mdbrain-id:    abc-123-with-spaces
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBe("abc-123-with-spaces");
    });
  });
});

describe("getClientId", () => {
  let mockApp: MockApp;
  let mockFile: MockTFile;
  let app: App;
  let file: TFile;

  beforeEach(() => {
    mockApp = new MockApp();
    mockFile = new MockTFile("test.md");
    app = mockApp as unknown as App;
    file = mockFile as unknown as TFile;
  });

  test("returns existing ID from cache", async () => {
    const content = `---
mdbrain-id: existing-id
---
# Content`;
    mockApp.vault.setFileContent("test.md", content);
    mockApp.metadataCache.setFileCache("test.md", {
      frontmatter: { "mdbrain-id": "existing-id" },
    });

    const id = await getClientId(file, app);
    expect(id).toBe("existing-id");
  });

  test("returns existing ID from content when cache is empty", async () => {
    const content = `---
mdbrain-id: content-id
---
# Content`;
    mockApp.vault.setFileContent("test.md", content);

    const id = await getClientId(file, app);
    expect(id).toBe("content-id");
  });

  test("returns null when no ID exists", async () => {
    mockApp.vault.setFileContent("test.md", "# No frontmatter");

    const id = await getClientId(file, app);
    expect(id).toBeNull();
  });

  test("returns null when frontmatter exists but no ID", async () => {
    const content = `---
title: Test
---
# Content`;
    mockApp.vault.setFileContent("test.md", content);

    const id = await getClientId(file, app);
    expect(id).toBeNull();
  });

  test("does not write to file", async () => {
    const content = "# No frontmatter";
    mockApp.vault.setFileContent("test.md", content);

    await getClientId(file, app);

    const afterContent = await mockApp.vault.read(mockFile);
    expect(afterContent).toBe(content);
    expect(afterContent).not.toContain("mdbrain-id");
  });
});

describe("ensureClientId", () => {
  let mockApp: MockApp;
  let mockFile: MockTFile;
  let app: App;
  let file: TFile;

  beforeEach(() => {
    mockApp = new MockApp();
    mockFile = new MockTFile("test.md");
    app = mockApp as unknown as App;
    file = mockFile as unknown as TFile;
  });

  test("returns existing ID without writing", async () => {
    const content = `---
mdbrain-id: existing-id
---
# Content`;
    mockApp.vault.setFileContent("test.md", content);

    const id = await ensureClientId(file, app);

    expect(id).toBe("existing-id");
    const afterContent = await mockApp.vault.read(mockFile);
    expect(afterContent).toBe(content);
  });

  test("generates and writes new ID when missing", async () => {
    mockApp.vault.setFileContent("test.md", "# No frontmatter");

    const id = await ensureClientId(file, app);

    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    const afterContent = await mockApp.vault.read(mockFile);
    expect(afterContent).toContain(`mdbrain-id: ${id}`);
  });

  test("adds ID to existing frontmatter", async () => {
    const content = `---
title: Test
---
# Content`;
    mockApp.vault.setFileContent("test.md", content);

    const id = await ensureClientId(file, app);

    const afterContent = await mockApp.vault.read(mockFile);
    expect(afterContent).toContain("title: Test");
    expect(afterContent).toContain(`mdbrain-id: ${id}`);
  });
});
