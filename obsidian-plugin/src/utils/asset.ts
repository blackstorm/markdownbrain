export const ASSET_EXTENSIONS = new Set([
  "png",
  "jpg",
  "jpeg",
  "gif",
  "webp",
  "svg",
  "bmp",
  "ico",
  "pdf",
  "mp3",
  "ogg",
  "wav",
  "mp4",
  "webm",
]);

export const RESOURCE_EXTENSIONS = ASSET_EXTENSIONS;

export interface FileWithExtension {
  extension: string;
}

export function isAssetFile(file: FileWithExtension): boolean {
  return ASSET_EXTENSIONS.has(file.extension.toLowerCase());
}

export const isResourceFile = isAssetFile;
