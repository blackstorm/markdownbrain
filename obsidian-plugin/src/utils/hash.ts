/**
 * Hash utilities
 * 
 * Pure functions for hashing data using Web Crypto API.
 * No Obsidian dependencies - fully unit-testable.
 */

/**
 * Compute SHA-256 hash of an ArrayBuffer
 * @param buffer - ArrayBuffer to hash
 * @returns Hex-encoded SHA-256 hash (64 characters)
 */
export async function sha256Hash(buffer: ArrayBuffer): Promise<string> {
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Compute SHA-256 hash of a string
 * @param str - String to hash
 * @returns Hex-encoded SHA-256 hash (64 characters)
 */
export async function hashString(str: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(str);
  return sha256Hash(data.buffer);
}
