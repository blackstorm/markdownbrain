export type ReferenceKind = "note" | "asset";

export interface ResolvedReference {
  path: string;
  kind: ReferenceKind;
}

export type ReferenceResolver = (linkpath: string, sourcePath: string) => ResolvedReference | null;

export interface ReferenceDiff {
  addedAssets: Set<string>;
  removedAssets: Set<string>;
  addedNotes: Set<string>;
  removedNotes: Set<string>;
}

const emptySet = (): Set<string> => new Set();

export class ReferenceIndex {
  private resolve: ReferenceResolver;

  private noteToAssets = new Map<string, Set<string>>();
  private noteToNotes = new Map<string, Set<string>>();
  private assetToNotes = new Map<string, Set<string>>();

  constructor(resolver: ReferenceResolver) {
    this.resolve = resolver;
  }

  updateNote(notePath: string, linkpaths: string[]): ReferenceDiff {
    const prevAssets = this.noteToAssets.get(notePath) ?? emptySet();
    const prevNotes = this.noteToNotes.get(notePath) ?? emptySet();

    const nextAssets = new Set<string>();
    const nextNotes = new Set<string>();

    for (const linkpath of linkpaths) {
      const resolved = this.resolve(linkpath, notePath);
      if (!resolved) continue;
      if (resolved.kind === "asset") {
        nextAssets.add(resolved.path);
      } else {
        nextNotes.add(resolved.path);
      }
    }

    const addedAssets = new Set<string>();
    const removedAssets = new Set<string>();
    for (const assetPath of nextAssets) {
      if (!prevAssets.has(assetPath)) addedAssets.add(assetPath);
    }
    for (const assetPath of prevAssets) {
      if (!nextAssets.has(assetPath)) removedAssets.add(assetPath);
    }

    const addedNotes = new Set<string>();
    const removedNotes = new Set<string>();
    for (const linkedNote of nextNotes) {
      if (!prevNotes.has(linkedNote)) addedNotes.add(linkedNote);
    }
    for (const linkedNote of prevNotes) {
      if (!nextNotes.has(linkedNote)) removedNotes.add(linkedNote);
    }

    for (const assetPath of removedAssets) {
      const notes = this.assetToNotes.get(assetPath);
      if (notes) {
        notes.delete(notePath);
        if (notes.size === 0) this.assetToNotes.delete(assetPath);
      }
    }
    for (const assetPath of addedAssets) {
      const notes = this.assetToNotes.get(assetPath) ?? new Set<string>();
      notes.add(notePath);
      this.assetToNotes.set(assetPath, notes);
    }

    this.noteToAssets.set(notePath, nextAssets);
    this.noteToNotes.set(notePath, nextNotes);

    return { addedAssets, removedAssets, addedNotes, removedNotes };
  }

  removeNote(notePath: string): void {
    const assets = this.noteToAssets.get(notePath);
    if (assets) {
      for (const assetPath of assets) {
        const notes = this.assetToNotes.get(assetPath);
        if (notes) {
          notes.delete(notePath);
          if (notes.size === 0) this.assetToNotes.delete(assetPath);
        }
      }
    }

    this.noteToAssets.delete(notePath);
    this.noteToNotes.delete(notePath);
  }

  renameNote(oldPath: string, newPath: string): void {
    if (oldPath === newPath) return;

    const assets = this.noteToAssets.get(oldPath);
    const notes = this.noteToNotes.get(oldPath);

    if (assets) {
      this.noteToAssets.delete(oldPath);
      this.noteToAssets.set(newPath, assets);
      for (const assetPath of assets) {
        const noteSet = this.assetToNotes.get(assetPath);
        if (!noteSet) continue;
        if (noteSet.delete(oldPath)) {
          noteSet.add(newPath);
        }
      }
    }

    if (notes) {
      this.noteToNotes.delete(oldPath);
      this.noteToNotes.set(newPath, notes);
    }
  }

  getAssetsForNote(notePath: string): Set<string> {
    return new Set(this.noteToAssets.get(notePath) ?? []);
  }

  getLinkedNotesForNote(notePath: string): Set<string> {
    return new Set(this.noteToNotes.get(notePath) ?? []);
  }

  getNotesForAsset(assetPath: string): Set<string> {
    return new Set(this.assetToNotes.get(assetPath) ?? []);
  }

  isAssetReferenced(assetPath: string): boolean {
    const notes = this.assetToNotes.get(assetPath);
    return Boolean(notes && notes.size > 0);
  }
}

