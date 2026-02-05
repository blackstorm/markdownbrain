import { type App, parseYaml, type TFile } from "obsidian";

export const CLIENT_ID_KEY = "mdbrain-id";

export async function getClientId(file: TFile, app: App): Promise<string | null> {
  const cache = app.metadataCache.getFileCache(file);
  if (cache?.frontmatter?.[CLIENT_ID_KEY]) {
    return String(cache.frontmatter[CLIENT_ID_KEY]).trim();
  }

  const fileContent = await app.vault.read(file);
  const frontmatterMatch = fileContent.match(/^---\n([\s\S]*?)\n---/);
  if (frontmatterMatch) {
    try {
      const frontmatter = parseYaml(frontmatterMatch[1]);
      if (frontmatter?.[CLIENT_ID_KEY]) {
        return String(frontmatter[CLIENT_ID_KEY]).trim();
      }
    } catch {
      // YAML parse error
    }
  }

  return null;
}

export async function ensureClientId(file: TFile, app: App): Promise<string> {
  const existingId = await getClientId(file, app);
  if (existingId) {
    return existingId;
  }

  const newId = crypto.randomUUID();
  await app.fileManager.processFrontMatter(file, (frontmatter) => {
    frontmatter[CLIENT_ID_KEY] = newId;
  });

  return newId;
}

/** @deprecated Use getClientId or ensureClientId instead */
export async function getOrCreateClientId(file: TFile, content: string, app: App): Promise<string> {
  const cache = app.metadataCache.getFileCache(file);
  if (cache?.frontmatter?.[CLIENT_ID_KEY]) {
    return String(cache.frontmatter[CLIENT_ID_KEY]).trim();
  }

  const fileContent = await app.vault.read(file);
  const latestContent = fileContent || content;

  const frontmatterMatch = latestContent.match(/^---\n([\s\S]*?)\n---/);
  if (frontmatterMatch) {
    try {
      const frontmatter = parseYaml(frontmatterMatch[1]);
      if (frontmatter?.[CLIENT_ID_KEY]) {
        return String(frontmatter[CLIENT_ID_KEY]).trim();
      }
    } catch {
      // YAML parse error, will generate new ID
    }
  }

  const newId = crypto.randomUUID();

  await app.fileManager.processFrontMatter(file, (frontmatter) => {
    frontmatter[CLIENT_ID_KEY] = newId;
  });

  return newId;
}
