import { describe, expect, test } from "vitest";
import { hashString, md5Hash } from "./hash";

describe("hash utils", () => {
  describe("md5Hash", () => {
    test("should hash empty buffer", async () => {
      const buffer = new ArrayBuffer(0);
      const result = await md5Hash(buffer);
      expect(result).toBe("d41d8cd98f00b204e9800998ecf8427e");
    });

    test("should hash simple string as buffer", async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode("hello").buffer;
      const result = await md5Hash(buffer);
      expect(result).toBe("5d41402abc4b2a76b9719d911017c592");
    });

    test("should return 32-character hex string", async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode("test data").buffer;
      const result = await md5Hash(buffer);
      expect(result).toHaveLength(32);
      expect(result).toMatch(/^[0-9a-f]{32}$/);
    });

    test("should be deterministic", async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode("test").buffer;
      const result1 = await md5Hash(buffer);
      const result2 = await md5Hash(buffer);
      expect(result1).toBe(result2);
    });

    test("should produce different hashes for different inputs", async () => {
      const encoder = new TextEncoder();
      const buffer1 = encoder.encode("hello").buffer;
      const buffer2 = encoder.encode("world").buffer;
      const result1 = await md5Hash(buffer1);
      const result2 = await md5Hash(buffer2);
      expect(result1).not.toBe(result2);
    });
  });

  describe("hashString", () => {
    test("should hash empty string", async () => {
      const result = await hashString("");
      expect(result).toBe("d41d8cd98f00b204e9800998ecf8427e");
    });

    test("should hash simple string", async () => {
      const result = await hashString("hello");
      expect(result).toBe("5d41402abc4b2a76b9719d911017c592");
    });

    test("should be deterministic", async () => {
      const result1 = await hashString("test content");
      const result2 = await hashString("test content");
      expect(result1).toBe(result2);
    });

    test("should produce different hashes for different inputs", async () => {
      const result1 = await hashString("hello");
      const result2 = await hashString("world");
      expect(result1).not.toBe(result2);
    });

    test("should handle unicode strings", async () => {
      const result = await hashString("你好世界");
      expect(result).toBe("65396ee4aad0b4f17aacd1c6112ee364");
    });
  });
});
