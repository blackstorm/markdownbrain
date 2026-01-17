/**
 * Hash utilities
 * 
 * Pure functions for hashing data using Node crypto.
 * No Obsidian dependencies - fully unit-testable.
 */

import { createHash } from 'crypto';

/**
 * Compute MD5 hash of an ArrayBuffer
 * @param buffer - ArrayBuffer to hash
 * @returns Hex-encoded MD5 hash (32 characters)
 */
export async function md5Hash(buffer: ArrayBuffer): Promise<string> {
  const hash = createHash('md5');
  hash.update(new Uint8Array(buffer));
  return hash.digest('hex');
}

/**
 * Compute MD5 hash of a string
 * @param str - String to hash
 * @returns Hex-encoded MD5 hash (32 characters)
 */
export async function hashString(str: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(str);
  return md5Hash(data.buffer);
}
