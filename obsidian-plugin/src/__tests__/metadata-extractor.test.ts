import { describe, test, expect } from 'bun:test';
import { extractNoteMetadata, type ObsidianCachedMetadata } from '../services/metadata-extractor';

describe('extractNoteMetadata', () => {
    describe('tags extraction', () => {
        test('extracts tags with position', () => {
            const cached: ObsidianCachedMetadata = {
                tags: [
                    { tag: '#javascript', position: { start: { line: 5, col: 0, offset: 100 }, end: { line: 5, col: 11, offset: 111 } } },
                    { tag: '#typescript', position: { start: { line: 6, col: 0, offset: 120 }, end: { line: 6, col: 11, offset: 131 } } },
                ],
            };

            const result = extractNoteMetadata(cached);

            expect(result.tags).toEqual([
                { tag: '#javascript', position: { start: { line: 5, col: 0, offset: 100 }, end: { line: 5, col: 11, offset: 111 } } },
                { tag: '#typescript', position: { start: { line: 6, col: 0, offset: 120 }, end: { line: 6, col: 11, offset: 131 } } },
            ]);
        });

        test('returns undefined tags when no tags present', () => {
            const cached: ObsidianCachedMetadata = {};

            const result = extractNoteMetadata(cached);

            expect(result.tags).toBeUndefined();
        });
    });

    describe('headings extraction', () => {
        test('extracts headings with level and position', () => {
            const cached: ObsidianCachedMetadata = {
                headings: [
                    { heading: 'Introduction', level: 1, position: { start: { line: 0, col: 0, offset: 0 }, end: { line: 0, col: 14, offset: 14 } } },
                    { heading: 'Getting Started', level: 2, position: { start: { line: 10, col: 0, offset: 100 }, end: { line: 10, col: 18, offset: 118 } } },
                ],
            };

            const result = extractNoteMetadata(cached);

            expect(result.headings).toEqual([
                { heading: 'Introduction', level: 1, position: { start: { line: 0, col: 0, offset: 0 }, end: { line: 0, col: 14, offset: 14 } } },
                { heading: 'Getting Started', level: 2, position: { start: { line: 10, col: 0, offset: 100 }, end: { line: 10, col: 18, offset: 118 } } },
            ]);
        });

        test('returns undefined headings when no headings present', () => {
            const cached: ObsidianCachedMetadata = {};

            const result = extractNoteMetadata(cached);

            expect(result.headings).toBeUndefined();
        });
    });

    describe('frontmatter extraction', () => {
        test('extracts frontmatter object', () => {
            const cached: ObsidianCachedMetadata = {
                frontmatter: {
                    title: 'My Note',
                    author: 'John Doe',
                    tags: ['tag1', 'tag2'],
                    published: true,
                },
            };

            const result = extractNoteMetadata(cached);

            expect(result.frontmatter).toEqual({
                title: 'My Note',
                author: 'John Doe',
                tags: ['tag1', 'tag2'],
                published: true,
            });
        });

        test('returns undefined frontmatter when not present', () => {
            const cached: ObsidianCachedMetadata = {};

            const result = extractNoteMetadata(cached);

            expect(result.frontmatter).toBeUndefined();
        });
    });

    describe('combined extraction', () => {
        test('extracts all metadata types together', () => {
            const cached: ObsidianCachedMetadata = {
                tags: [
                    { tag: '#test', position: { start: { line: 5, col: 0, offset: 50 }, end: { line: 5, col: 5, offset: 55 } } },
                ],
                headings: [
                    { heading: 'Title', level: 1, position: { start: { line: 0, col: 0, offset: 0 }, end: { line: 0, col: 7, offset: 7 } } },
                ],
                frontmatter: {
                    title: 'Test Note',
                },
            };

            const result = extractNoteMetadata(cached);

            expect(result.tags?.length).toBe(1);
            expect(result.headings?.length).toBe(1);
            expect(result.frontmatter).toEqual({ title: 'Test Note' });
        });

        test('returns empty object when cached metadata is null', () => {
            const result = extractNoteMetadata(null);

            expect(result).toEqual({});
        });

        test('returns empty object when cached metadata is undefined', () => {
            const result = extractNoteMetadata(undefined);

            expect(result).toEqual({});
        });
    });
});
