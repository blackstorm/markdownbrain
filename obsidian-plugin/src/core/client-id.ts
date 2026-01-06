import { TFile, App } from 'obsidian';

/**
 * Get or create a client ID from frontmatter.
 * If the file already has an 'id' field in frontmatter, return it.
 * Otherwise, generate a new UUID and write it to frontmatter.
 *
 * @param file - The file to get or create the client ID for
 * @param content - The current content of the file (used as fallback if file is empty)
 * @param app - The Obsidian app instance
 * @returns The client ID (existing or newly created)
 */
export async function getOrCreateClientId(
  file: TFile,
  content: string,
  app: App
): Promise<string> {
  // Read from file, but use the passed content as fallback for empty files (useful for tests)
  const fileContent = await app.vault.read(file);
  const latestContent = fileContent || content;

  // Step 1: Try to parse frontmatter and extract existing id
  const frontmatterMatch = latestContent.match(/^---\n([\s\S]*?)\n---/);

  if (frontmatterMatch) {
    const frontmatter = frontmatterMatch[1];
    const idMatch = frontmatter.match(/^id:\s*(.+)$/m);

    if (idMatch) {
      // ID exists in frontmatter, return it
      return idMatch[1].trim();
    }

    // Frontmatter exists but no ID, add it
    const newId = crypto.randomUUID();
    const newFrontmatter = frontmatter.trim() + `\nid: ${newId}`;
    const newContent = latestContent.replace(
      /^---\n[\s\S]*?\n---/,
      `---\n${newFrontmatter}\n---`
    );
    await app.vault.process(file, () => newContent);
    return newId;
  }

  // Step 2: No frontmatter, create it with ID
  const newId = crypto.randomUUID();
  const newContent = `---\nid: ${newId}\n---\n\n${latestContent}`;
  await app.vault.process(file, () => newContent);
  return newId;
}
