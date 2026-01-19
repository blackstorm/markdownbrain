import type { App, TFile } from "obsidian";
import { beforeEach, describe, expect, test } from "vitest";
import { MockApp, MockTFile } from "../test/setup";
import { getOrCreateClientId } from "./client-id";

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

  describe("File with existing frontmatter markdownbrain-id", () => {
    test("should return existing UUID from frontmatter", async () => {
      const content = `---
markdownbrain-id: abc-123-def
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBe("abc-123-def");
    });

    test("should return existing UUID with different format", async () => {
      const content = `---
markdownbrain-id: 550e8400-e29b-41d4-a716-446655440000
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
      expect(updatedContent).toContain("markdownbrain-id:");
      expect(updatedContent).toContain(fileId);
    });

    test("should preserve existing content when adding frontmatter", async () => {
      const content = "# Hello World\n\nThis is content.";
      mockApp.vault.setFileContent("test.md", content);

      await getOrCreateClientId(file, content, app);

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain("# Hello World");
      expect(updatedContent).toContain("This is content.");
      expect(updatedContent).toContain("markdownbrain-id:");
    });
  });

  describe("File with frontmatter but no markdownbrain-id", () => {
    test("should add markdownbrain-id field to existing frontmatter", async () => {
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
      expect(updatedContent).toContain(`markdownbrain-id: ${fileId}`);
    });

    test("should add markdownbrain-id to frontmatter with arrays", async () => {
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
      expect(updatedContent).toContain(`markdownbrain-id: ${fileId}`);
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

    test("should not add multiple markdownbrain-id fields on repeated calls", async () => {
      const content = "# Content";
      mockApp.vault.setFileContent("test.md", content);

      await getOrCreateClientId(file, content, app);
      await getOrCreateClientId(file, content, app);

      const updatedContent = await mockApp.vault.read(mockFile);
      const matches = updatedContent.match(/markdownbrain-id:/g);
      expect(matches?.length).toBe(1);
    });
  });

  describe("Rename scenarios", () => {
    test("should preserve UUID after rename (same content, different path)", async () => {
      const oldFile = new MockTFile("old.md");
      const newFile = new MockTFile("new.md");
      const content = `---
markdownbrain-id: preserved-uuid
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
      expect(updatedContent).toContain("markdownbrain-id:");
    });

    test("should handle markdownbrain-id field with extra spaces", async () => {
      const content = `---
markdownbrain-id:    abc-123-with-spaces
---
# Content`;

      mockApp.vault.setFileContent("test.md", content);

      const fileId = await getOrCreateClientId(file, content, app);

      expect(fileId).toBe("abc-123-with-spaces");
    });
  });
});
