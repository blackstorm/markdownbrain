import { describe, test, expect } from 'bun:test';
import { RESOURCE_EXTENSIONS, isResourceFile } from '../utils/resource';

describe('resource utils', () => {
  describe('RESOURCE_EXTENSIONS', () => {
    test('should include image extensions', () => {
      expect(RESOURCE_EXTENSIONS.has('png')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('jpg')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('jpeg')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('gif')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('webp')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('svg')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('bmp')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('ico')).toBe(true);
    });

    test('should include pdf', () => {
      expect(RESOURCE_EXTENSIONS.has('pdf')).toBe(true);
    });

    test('should include audio extensions', () => {
      expect(RESOURCE_EXTENSIONS.has('mp3')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('ogg')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('wav')).toBe(true);
    });

    test('should include video extensions', () => {
      expect(RESOURCE_EXTENSIONS.has('mp4')).toBe(true);
      expect(RESOURCE_EXTENSIONS.has('webm')).toBe(true);
    });

    test('should NOT include markdown', () => {
      expect(RESOURCE_EXTENSIONS.has('md')).toBe(false);
    });
  });

  describe('isResourceFile', () => {
    test('should return true for image files', () => {
      expect(isResourceFile({ extension: 'png' })).toBe(true);
      expect(isResourceFile({ extension: 'PNG' })).toBe(true);
      expect(isResourceFile({ extension: 'jpg' })).toBe(true);
      expect(isResourceFile({ extension: 'JPG' })).toBe(true);
    });

    test('should return true for pdf files', () => {
      expect(isResourceFile({ extension: 'pdf' })).toBe(true);
      expect(isResourceFile({ extension: 'PDF' })).toBe(true);
    });

    test('should return true for audio files', () => {
      expect(isResourceFile({ extension: 'mp3' })).toBe(true);
      expect(isResourceFile({ extension: 'wav' })).toBe(true);
    });

    test('should return true for video files', () => {
      expect(isResourceFile({ extension: 'mp4' })).toBe(true);
      expect(isResourceFile({ extension: 'webm' })).toBe(true);
    });

    test('should return false for markdown files', () => {
      expect(isResourceFile({ extension: 'md' })).toBe(false);
    });

    test('should return false for text files', () => {
      expect(isResourceFile({ extension: 'txt' })).toBe(false);
    });

    test('should return false for json files', () => {
      expect(isResourceFile({ extension: 'json' })).toBe(false);
    });
  });
});
