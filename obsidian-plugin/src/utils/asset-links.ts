import { isAssetFile } from "./asset";

const CODE_BLOCK_RE = /```[\s\S]*?```|~~~[\s\S]*?~~~/g;
const INLINE_CODE_RE = /`[^`\n]*`/g;

const stripCodeBlocks = (content: string): string =>
  content.replace(CODE_BLOCK_RE, "").replace(INLINE_CODE_RE, "");

const decodePath = (value: string): string => {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
};

const sanitizePath = (raw: string): string => {
  const trimmed = raw.trim().replace(/^['"]|['"]$/g, "");
  const unwrapped = trimmed.replace(/^<|>$/g, "");
  const decoded = decodePath(unwrapped);
  const normalized = decoded.replace(/\\/g, "/").replace(/^\.\//, "");
  const cutAt = normalized.search(/[?#]/);
  return cutAt >= 0 ? normalized.slice(0, cutAt) : normalized;
};

const looksLikeAsset = (path: string): boolean => {
  const parts = path.split("/");
  const filename = parts[parts.length - 1] ?? "";
  const ext = filename.includes(".") ? (filename.split(".").pop() ?? "") : "";
  return isAssetFile({ extension: ext });
};

const extractWikiPaths = (content: string): string[] => {
  const matches = content.matchAll(/!?\[\[([^\]]+)\]\]/g);
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
};

const extractInlineImagePaths = (content: string): string[] => {
  const matches = content.matchAll(/!\[[^\]]*\]\(([^)]+)\)/g);
  const results: string[] = [];
  for (const match of matches) {
    const inner = match[1] ?? "";
    let candidate = inner.trim();
    if (candidate.startsWith("<") && candidate.includes(">")) {
      candidate = candidate.slice(1, candidate.indexOf(">"));
    } else {
      candidate = candidate.split(/\s+/)[0] ?? "";
    }
    const cleaned = sanitizePath(candidate);
    if (cleaned) {
      results.push(cleaned);
    }
  }
  return results;
};

const normalizeLabel = (label: string): string => label.trim().toLowerCase().replace(/\s+/g, " ");

const extractReferenceDefinitions = (content: string): Map<string, string> => {
  const definitions = new Map<string, string>();
  const matches = content.matchAll(/^\s*\[([^\]]+)\]:\s+(.+)$/gm);
  for (const match of matches) {
    const label = normalizeLabel(match[1] ?? "");
    const raw = match[2] ?? "";
    const destination = parseLinkDestination(raw);
    if (label && destination) {
      definitions.set(label, destination);
    }
  }
  return definitions;
};

const parseLinkDestination = (raw: string): string => {
  const trimmed = raw.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("<")) {
    const end = trimmed.indexOf(">");
    if (end > 1) {
      return sanitizePath(trimmed.slice(1, end));
    }
  }
  const token = trimmed.split(/\s+/)[0] ?? "";
  return sanitizePath(token);
};

const extractReferenceImagePaths = (content: string): string[] => {
  const definitions = extractReferenceDefinitions(content);
  const matches = content.matchAll(/!\[([^\]]*)\]\[([^\]]*)\]/g);
  const results: string[] = [];
  for (const match of matches) {
    const alt = match[1] ?? "";
    const label = match[2] ?? "";
    const resolvedLabel = normalizeLabel(label || alt);
    const destination = resolvedLabel ? (definitions.get(resolvedLabel) ?? "") : "";
    if (destination) {
      results.push(destination);
    }
  }
  return results;
};

const extractHtmlMediaPaths = (content: string): string[] => {
  const tags = content.matchAll(/<(img|audio|video|source)\b[^>]*>/gi);
  const results: string[] = [];
  for (const match of tags) {
    const tag = match[0] ?? "";
    const srcMatch = tag.match(/src\s*=\s*(?:"([^"]+)"|'([^']+)'|([^\s>]+))/i);
    const raw = srcMatch?.[1] ?? srcMatch?.[2] ?? srcMatch?.[3] ?? "";
    const cleaned = sanitizePath(raw);
    if (cleaned) {
      results.push(cleaned);
    }
  }
  return results;
};

const isExternalLink = (path: string): boolean => /^[a-z][a-z0-9+.-]*:/i.test(path);

const extractMarkdownLinkPaths = (content: string): string[] => {
  const matches = content.matchAll(/\[[^\]]*\]\(([^)]+)\)/g);
  const results: string[] = [];
  for (const match of matches) {
    const start = match.index ?? 0;
    if (start > 0 && content[start - 1] === "!") {
      continue;
    }
    const raw = match[1] ?? "";
    const destination = parseLinkDestination(raw);
    if (destination && !isExternalLink(destination)) {
      results.push(destination);
    }
  }
  return results;
};

export function extractAssetPaths(content: string): string[] {
  const sanitizedContent = stripCodeBlocks(content);
  const wikiPaths = extractWikiPaths(sanitizedContent);
  const inlineImages = extractInlineImagePaths(sanitizedContent);
  const referenceImages = extractReferenceImagePaths(sanitizedContent);
  const htmlMedia = extractHtmlMediaPaths(sanitizedContent);
  return [...wikiPaths, ...inlineImages, ...referenceImages, ...htmlMedia].filter(looksLikeAsset);
}

export function extractNotePaths(content: string): string[] {
  const sanitizedContent = stripCodeBlocks(content);
  const wikiPaths = extractWikiPaths(sanitizedContent);
  const markdownLinks = extractMarkdownLinkPaths(sanitizedContent);
  return [...wikiPaths, ...markdownLinks];
}
