import { describe, test, expect } from 'bun:test';
import { sha256Hash, hashString } from '../utils/hash';

describe('hash utils', () => {
  describe('sha256Hash', () => {
    test('should hash empty buffer', async () => {
      const buffer = new ArrayBuffer(0);
      const result = await sha256Hash(buffer);
      // SHA-256 of empty input is well-known
      expect(result).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    });

    test('should hash simple string as buffer', async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode('hello').buffer;
      const result = await sha256Hash(buffer);
      // SHA-256 of "hello" is well-known
      expect(result).toBe('2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824');
    });

    test('should return 64-character hex string', async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode('test data').buffer;
      const result = await sha256Hash(buffer);
      expect(result).toHaveLength(64);
      expect(result).toMatch(/^[0-9a-f]{64}$/);
    });

    test('should be deterministic', async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode('test').buffer;
      const result1 = await sha256Hash(buffer);
      const result2 = await sha256Hash(buffer);
      expect(result1).toBe(result2);
    });

    test('should produce different hashes for different inputs', async () => {
      const encoder = new TextEncoder();
      const buffer1 = encoder.encode('hello').buffer;
      const buffer2 = encoder.encode('world').buffer;
      const result1 = await sha256Hash(buffer1);
      const result2 = await sha256Hash(buffer2);
      expect(result1).not.toBe(result2);
    });
  });

  describe('hashString', () => {
    test('should hash empty string', async () => {
      const result = await hashString('');
      expect(result).toHaveLength(64);
      expect(result).toMatch(/^[0-9a-f]{64}$/);
    });

    test('should hash simple string', async () => {
      const result = await hashString('hello');
      expect(result).toBe('2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824');
    });

    test('should be deterministic', async () => {
      const result1 = await hashString('test content');
      const result2 = await hashString('test content');
      expect(result1).toBe(result2);
    });

    test('should produce different hashes for different inputs', async () => {
      const result1 = await hashString('hello');
      const result2 = await hashString('world');
      expect(result1).not.toBe(result2);
    });

    test('should handle unicode strings', async () => {
      const result = await hashString('你好世界');
      expect(result).toHaveLength(64);
      expect(result).toMatch(/^[0-9a-f]{64}$/);
    });
  });
});
