/**
 * Encoding utilities
 * 
 * Pure functions for encoding binary data.
 * No Obsidian dependencies - fully unit-testable.
 */

/**
 * Convert ArrayBuffer to base64 string
 * @param buffer - ArrayBuffer to encode
 * @returns Base64 encoded string
 */
export async function arrayBufferToBase64(buffer: ArrayBuffer): Promise<string> {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}
