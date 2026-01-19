import { isAssetFile } from "./asset";

function sanitizePath(raw: string): string {
  return raw.trim().replace(/^['"]|['"]$/g, "");
}

function extractEmbedPaths(content: string): string[] {
  const matches = content.matchAll(/!\[\[([^\]]+)\]\]/g);
  const results: string[] = [];
  for (const match of matches) {
    const inner = match[1] ?? "";
    const [pathPart] = inner.split("|");
    const [path] = pathPart.split("#");
    const cleaned = sanitizePath(path);
    if (cleaned) {
      results.push(cleaned);
    }
  }
  return results;
}

function extractMarkdownImagePaths(content: string): string[] {
  const matches = content.matchAll(/!\[[^\]]*\]\(([^)]+)\)/g);
  const results: string[] = [];
  for (const match of matches) {
    const inner = match[1] ?? "";
    const [path] = inner.split(/\s+/);
    const cleaned = sanitizePath(path);
    if (cleaned) {
      results.push(cleaned);
    }
  }
  return results;
}

function looksLikeAsset(path: string): boolean {
  const parts = path.split("/");
  const filename = parts[parts.length - 1] ?? "";
  const ext = filename.includes(".") ? (filename.split(".").pop() ?? "") : "";
  return isAssetFile({ extension: ext });
}

export function extractAssetPaths(content: string): string[] {
  const embeds = extractEmbedPaths(content);
  const markdownImages = extractMarkdownImagePaths(content);
  return [...embeds, ...markdownImages].filter(looksLikeAsset);
}
