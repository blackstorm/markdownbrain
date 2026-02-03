import { describe, expect, test } from "vitest";
import { arrayBufferToBase64 } from "./encoding";

describe("encoding utils", () => {
  describe("arrayBufferToBase64", () => {
    test("should encode empty buffer", async () => {
      const buffer = new ArrayBuffer(0);
      const result = await arrayBufferToBase64(buffer);
      expect(result).toBe("");
    });

    test("should encode simple string as buffer", async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode("Hello").buffer;
      const result = await arrayBufferToBase64(buffer);
      expect(result).toBe("SGVsbG8=");
    });

    test("should encode binary data correctly", async () => {
      // Create buffer with bytes [0, 1, 2, 3]
      const buffer = new Uint8Array([0, 1, 2, 3]).buffer;
      const result = await arrayBufferToBase64(buffer);
      expect(result).toBe("AAECAw==");
    });

    test("should handle special characters", async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode("Hello, 世界!").buffer;
      const result = await arrayBufferToBase64(buffer);
      // Verify it's valid base64 by decoding
      expect(() => Buffer.from(result, "base64")).not.toThrow();
    });

    test("should be deterministic", async () => {
      const encoder = new TextEncoder();
      const buffer = encoder.encode("test data").buffer;
      const result1 = await arrayBufferToBase64(buffer);
      const result2 = await arrayBufferToBase64(buffer);
      expect(result1).toBe(result2);
    });
  });
});
