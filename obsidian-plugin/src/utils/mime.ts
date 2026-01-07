/**
 * MIME type utilities
 * 
 * Pure functions for determining MIME types from file extensions.
 * No Obsidian dependencies - fully unit-testable.
 */

/**
 * Mapping of file extensions to MIME types
 */
export const MIME_TYPES: Record<string, string> = {
  // Images
  'png': 'image/png',
  'jpg': 'image/jpeg',
  'jpeg': 'image/jpeg',
  'gif': 'image/gif',
  'webp': 'image/webp',
  'svg': 'image/svg+xml',
  'bmp': 'image/bmp',
  'ico': 'image/x-icon',
  // Documents
  'pdf': 'application/pdf',
  // Audio
  'mp3': 'audio/mpeg',
  'ogg': 'audio/ogg',
  'wav': 'audio/wav',
  // Video
  'mp4': 'video/mp4',
  'webm': 'video/webm'
};

/**
 * Get MIME type for a file extension
 * @param extension - File extension (with or without dot)
 * @returns MIME type string, or 'application/octet-stream' for unknown extensions
 */
export function getContentType(extension: string): string {
  const ext = extension.toLowerCase();
  return MIME_TYPES[ext] || 'application/octet-stream';
}
