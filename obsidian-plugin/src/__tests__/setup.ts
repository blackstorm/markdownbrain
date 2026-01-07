import { mock } from 'bun:test';

// Import mocks from __mocks__ directory
import * as obsidianMock from '../../__mocks__/obsidian';

// Re-export mock classes for test convenience
export const MockTFile = obsidianMock.TFile;
export const MockApp = obsidianMock.App;
export const MockVault = obsidianMock.Vault;
export const MockMetadataCache = obsidianMock.MetadataCache;
export const MockFileManager = obsidianMock.FileManager;
export const mockParseYaml = obsidianMock.parseYaml;
export const mockStringifyYaml = obsidianMock.stringifyYaml;

// Mock the obsidian module
mock.module('obsidian', () => obsidianMock);
