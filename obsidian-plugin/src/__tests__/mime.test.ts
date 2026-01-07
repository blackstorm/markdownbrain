import { describe, test, expect } from 'bun:test';
import { getContentType, MIME_TYPES } from '../utils/mime';

describe('mime utils', () => {
  describe('MIME_TYPES', () => {
    test('should have image mime types', () => {
      expect(MIME_TYPES['png']).toBe('image/png');
      expect(MIME_TYPES['jpg']).toBe('image/jpeg');
      expect(MIME_TYPES['jpeg']).toBe('image/jpeg');
      expect(MIME_TYPES['gif']).toBe('image/gif');
      expect(MIME_TYPES['webp']).toBe('image/webp');
      expect(MIME_TYPES['svg']).toBe('image/svg+xml');
      expect(MIME_TYPES['bmp']).toBe('image/bmp');
      expect(MIME_TYPES['ico']).toBe('image/x-icon');
    });

    test('should have pdf mime type', () => {
      expect(MIME_TYPES['pdf']).toBe('application/pdf');
    });

    test('should have audio mime types', () => {
      expect(MIME_TYPES['mp3']).toBe('audio/mpeg');
      expect(MIME_TYPES['ogg']).toBe('audio/ogg');
      expect(MIME_TYPES['wav']).toBe('audio/wav');
    });

    test('should have video mime types', () => {
      expect(MIME_TYPES['mp4']).toBe('video/mp4');
      expect(MIME_TYPES['webm']).toBe('video/webm');
    });
  });

  describe('getContentType', () => {
    test('should return correct mime type for known extensions', () => {
      expect(getContentType('png')).toBe('image/png');
      expect(getContentType('jpg')).toBe('image/jpeg');
      expect(getContentType('pdf')).toBe('application/pdf');
      expect(getContentType('mp3')).toBe('audio/mpeg');
      expect(getContentType('mp4')).toBe('video/mp4');
    });

    test('should be case-insensitive', () => {
      expect(getContentType('PNG')).toBe('image/png');
      expect(getContentType('JPG')).toBe('image/jpeg');
      expect(getContentType('PDF')).toBe('application/pdf');
      expect(getContentType('Mp3')).toBe('audio/mpeg');
    });

    test('should return application/octet-stream for unknown extensions', () => {
      expect(getContentType('xyz')).toBe('application/octet-stream');
      expect(getContentType('unknown')).toBe('application/octet-stream');
      expect(getContentType('')).toBe('application/octet-stream');
    });
  });
});
