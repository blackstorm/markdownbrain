import { describe, test, expect, beforeEach, mock } from 'bun:test';
import { TFile, App } from '../../__mocks__/obsidian';

// Mock the 'obsidian' module
mock.module('obsidian', () => ({
  TFile,
  App,
}));

// Now import the function after mocking
import { getOrCreateClientId } from '../core/client-id';

describe('getOrCreateClientId', () => {
  let mockApp: App;
  let mockFile: TFile;

  beforeEach(() => {
    mockApp = new App();
    mockFile = new TFile('test.md');
  });

  describe('File with existing frontmatter id', () => {
    test('should return existing UUID from frontmatter', async () => {
      const content = `---
id: abc-123-def
---
# Content`;

      mockApp.vault.setFileContent('test.md', content);

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBe('abc-123-def');
    });

    test('should return existing UUID with different format', async () => {
      const content = `---
id: 550e8400-e29b-41d4-a716-446655440000
---
# Content`;

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBe('550e8400-e29b-41d4-a716-446655440000');
    });
  });

  describe('File without frontmatter', () => {
    test('should generate new UUID and write to frontmatter', async () => {
      const content = '# Content without frontmatter';

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      // Should return a UUID
      expect(fileId).toBeDefined();
      expect(fileId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);

      // Should have updated the file
      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain('id:');
      expect(updatedContent).toContain(fileId);
      expect(updatedContent).toMatch(/^---\nid:/m);
    });

    test('should preserve existing content when adding frontmatter', async () => {
      const content = '# Hello World\n\nThis is content.';

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain('# Hello World');
      expect(updatedContent).toContain('This is content.');
      expect(updatedContent).toContain('id:');
    });
  });

  describe('File with frontmatter but no id', () => {
    test('should add id field to existing frontmatter', async () => {
      const content = `---
title: Test
tags:
  - example
---
# Content`;

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBeDefined();

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain('title: Test');
      expect(updatedContent).toContain('tags:');
      expect(updatedContent).toContain('id: ' + fileId);
    });

    test('should add id to frontmatter with arrays', async () => {
      const content = `---
tags:
  - test
  - example
---
# Content`;

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBeDefined();

      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toMatch(/tags:\n\s*-\s*test\n\s*-\s*example/);
      expect(updatedContent).toContain('id: ' + fileId);
    });
  });

  describe('Idempotency', () => {
    test('should return same UUID on multiple calls', async () => {
      const content = '# Content';

      const id1 = await getOrCreateClientId(mockFile, content, mockApp);
      const id2 = await getOrCreateClientId(mockFile, content, mockApp);

      expect(id1).toBe(id2);
    });

    test('should not add multiple id fields on repeated calls', async () => {
      const content = '# Content';

      await getOrCreateClientId(mockFile, content, mockApp);
      await getOrCreateClientId(mockFile, content, mockApp);

      const updatedContent = await mockApp.vault.read(mockFile);
      const matches = updatedContent.match(/^id:\s*.+$/gm);
      expect(matches?.length).toBe(1);
    });
  });

  describe('Rename scenarios', () => {
    test('should preserve UUID after rename (same content, different path)', async () => {
      const oldFile = new TFile('old.md');
      const newFile = new TFile('new.md');
      const content = `---
id: preserved-uuid
---
# Content`;

      mockApp.vault.setFileContent('old.md', content);
      mockApp.vault.setFileContent('new.md', content);

      const oldId = await getOrCreateClientId(oldFile, content, mockApp);
      const newId = await getOrCreateClientId(newFile, content, mockApp);

      expect(oldId).toBe('preserved-uuid');
      expect(newId).toBe('preserved-uuid');
      expect(oldId).toBe(newId);
    });
  });

  describe('Edge cases', () => {
    test('should handle empty files', async () => {
      const content = '';

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBeDefined();
      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain('id:');
    });

    test('should handle malformed frontmatter (incomplete)', async () => {
      const content = '---\nincomplete frontmatter without closing';

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBeDefined();
    });

    test('should handle non-standard YAML (colons in values)', async () => {
      const content = `---
key: "value: with: colons"
---
# Content`;

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBeDefined();
      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain('value: with: colons');
    });

    test('should handle frontmatter with only whitespace', async () => {
      const content = `---

---
# Content`;

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBeDefined();
      const updatedContent = await mockApp.vault.read(mockFile);
      expect(updatedContent).toContain('id:');
    });

    test('should handle id field with extra spaces', async () => {
      const content = `---
id:    abc-123-with-spaces
---
# Content`;

      const fileId = await getOrCreateClientId(mockFile, content, mockApp);

      expect(fileId).toBe('abc-123-with-spaces');
    });
  });
});
