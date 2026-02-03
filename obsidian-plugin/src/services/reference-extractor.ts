export interface ReferenceLike {
  link: string;
}

export interface FrontmatterLinkLike {
  link: string;
}

export interface CachedMetadataLike {
  links?: ReferenceLike[];
  embeds?: ReferenceLike[];
  frontmatterLinks?: FrontmatterLinkLike[];
  tags?: Array<{
    tag: string;
    position: {
      start: { line: number; col: number; offset: number };
      end: { line: number; col: number; offset: number };
    };
  }>;
  headings?: Array<{
    heading: string;
    level: number;
    position: {
      start: { line: number; col: number; offset: number };
      end: { line: number; col: number; offset: number };
    };
  }>;
  frontmatter?: Record<string, unknown>;
}

const stripSubpath = (link: string): string => {
  const trimmed = link.trim();
  if (!trimmed) return "";
  const hashIndex = trimmed.indexOf("#");
  return (hashIndex >= 0 ? trimmed.slice(0, hashIndex) : trimmed).trim();
};

const isExternalLink = (value: string): boolean => {
  const lower = value.toLowerCase();
  if (lower.startsWith("mailto:")) return true;
  if (lower.startsWith("obsidian:")) return true;
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(value);
};

export function extractInternalLinkpathsFromCache(
  cache: CachedMetadataLike | null | undefined,
): string[] {
  if (!cache) return [];

  const candidates: string[] = [];

  for (const ref of cache.links ?? []) {
    candidates.push(ref.link);
  }
  for (const ref of cache.embeds ?? []) {
    candidates.push(ref.link);
  }
  for (const ref of cache.frontmatterLinks ?? []) {
    candidates.push(ref.link);
  }

  const seen = new Set<string>();
  const results: string[] = [];

  for (const raw of candidates) {
    const linkpath = stripSubpath(String(raw ?? ""));
    if (!linkpath) continue;
    if (isExternalLink(linkpath)) continue;
    if (seen.has(linkpath)) continue;
    seen.add(linkpath);
    results.push(linkpath);
  }

  return results;
}
