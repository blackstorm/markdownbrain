import { describe, test, expect } from 'bun:test';
import { ASSET_EXTENSIONS, isAssetFile, RESOURCE_EXTENSIONS, isResourceFile } from '../utils/asset';

describe('asset utils', () => {
  describe('ASSET_EXTENSIONS', () => {
    test('should include image extensions', () => {
      expect(ASSET_EXTENSIONS.has('png')).toBe(true);
      expect(ASSET_EXTENSIONS.has('jpg')).toBe(true);
      expect(ASSET_EXTENSIONS.has('jpeg')).toBe(true);
      expect(ASSET_EXTENSIONS.has('gif')).toBe(true);
      expect(ASSET_EXTENSIONS.has('webp')).toBe(true);
      expect(ASSET_EXTENSIONS.has('svg')).toBe(true);
      expect(ASSET_EXTENSIONS.has('bmp')).toBe(true);
      expect(ASSET_EXTENSIONS.has('ico')).toBe(true);
    });

    test('should include pdf', () => {
      expect(ASSET_EXTENSIONS.has('pdf')).toBe(true);
    });

    test('should include audio extensions', () => {
      expect(ASSET_EXTENSIONS.has('mp3')).toBe(true);
      expect(ASSET_EXTENSIONS.has('ogg')).toBe(true);
      expect(ASSET_EXTENSIONS.has('wav')).toBe(true);
    });

    test('should include video extensions', () => {
      expect(ASSET_EXTENSIONS.has('mp4')).toBe(true);
      expect(ASSET_EXTENSIONS.has('webm')).toBe(true);
    });

    test('should NOT include markdown', () => {
      expect(ASSET_EXTENSIONS.has('md')).toBe(false);
    });
  });

  describe('isAssetFile', () => {
    test('should return true for image files', () => {
      expect(isAssetFile({ extension: 'png' })).toBe(true);
      expect(isAssetFile({ extension: 'PNG' })).toBe(true);
      expect(isAssetFile({ extension: 'jpg' })).toBe(true);
      expect(isAssetFile({ extension: 'JPG' })).toBe(true);
    });

    test('should return true for pdf files', () => {
      expect(isAssetFile({ extension: 'pdf' })).toBe(true);
      expect(isAssetFile({ extension: 'PDF' })).toBe(true);
    });

    test('should return true for audio files', () => {
      expect(isAssetFile({ extension: 'mp3' })).toBe(true);
      expect(isAssetFile({ extension: 'wav' })).toBe(true);
    });

    test('should return true for video files', () => {
      expect(isAssetFile({ extension: 'mp4' })).toBe(true);
      expect(isAssetFile({ extension: 'webm' })).toBe(true);
    });

    test('should return false for markdown files', () => {
      expect(isAssetFile({ extension: 'md' })).toBe(false);
    });

    test('should return false for text files', () => {
      expect(isAssetFile({ extension: 'txt' })).toBe(false);
    });

    test('should return false for json files', () => {
      expect(isAssetFile({ extension: 'json' })).toBe(false);
    });
  });

  describe('backward compatibility aliases', () => {
    test('RESOURCE_EXTENSIONS should be same as ASSET_EXTENSIONS', () => {
      expect(RESOURCE_EXTENSIONS).toBe(ASSET_EXTENSIONS);
    });

    test('isResourceFile should be same as isAssetFile', () => {
      expect(isResourceFile).toBe(isAssetFile);
    });
  });
});
