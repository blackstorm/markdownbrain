/**
 * Resource file utilities
 * 
 * Pure functions for determining if a file is a resource (image, pdf, audio, video).
 * No Obsidian dependencies - fully unit-testable.
 */

/**
 * Set of supported resource file extensions
 */
export const RESOURCE_EXTENSIONS = new Set([
  // Images
  'png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp', 'ico',
  // Documents
  'pdf',
  // Audio
  'mp3', 'ogg', 'wav',
  // Video
  'mp4', 'webm'
]);

/**
 * File-like object with extension property
 */
export interface FileWithExtension {
  extension: string;
}

/**
 * Check if a file is a resource file based on its extension
 * @param file - Object with extension property
 * @returns true if the file is a resource (image, pdf, audio, video)
 */
export function isResourceFile(file: FileWithExtension): boolean {
  return RESOURCE_EXTENSIONS.has(file.extension.toLowerCase());
}
