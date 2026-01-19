import { vi } from "vitest";

// Import mocks from test/mocks directory
import * as obsidianMock from "./mocks/obsidian";

// Re-export mock classes for test convenience
export const MockTFile = obsidianMock.TFile;
export const MockApp = obsidianMock.App;
export const MockVault = obsidianMock.Vault;
export const MockMetadataCache = obsidianMock.MetadataCache;
export const MockFileManager = obsidianMock.FileManager;
export const mockParseYaml = obsidianMock.parseYaml;
export const mockStringifyYaml = obsidianMock.stringifyYaml;

// Mock the obsidian module
vi.mock("obsidian", async () => await import("./mocks/obsidian"));
