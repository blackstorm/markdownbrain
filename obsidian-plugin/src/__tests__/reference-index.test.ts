import { describe, expect, test } from "vitest";
import { ReferenceIndex, type ReferenceResolver } from "../services/reference-index";

describe("ReferenceIndex", () => {
  const resolver: ReferenceResolver = (linkpath, _sourcePath) => {
    if (linkpath === "a.md") return { path: "a.md", kind: "note" };
    if (linkpath === "b.md") return { path: "b.md", kind: "note" };
    if (linkpath === "img.png") return { path: "img.png", kind: "asset" };
    if (linkpath === "docs/diagram.svg") return { path: "docs/diagram.svg", kind: "asset" };
    return null;
  };

  test("updates note->asset and asset->notes maps with diffs", () => {
    const index = new ReferenceIndex(resolver);

    const first = index.updateNote("notes/source.md", ["img.png", "docs/diagram.svg", "a.md"]);
    expect(first.addedAssets).toEqual(new Set(["img.png", "docs/diagram.svg"]));
    expect(first.removedAssets.size).toBe(0);
    expect(first.addedNotes).toEqual(new Set(["a.md"]));
    expect(first.removedNotes.size).toBe(0);

    expect(index.isAssetReferenced("img.png")).toBe(true);
    expect(index.getNotesForAsset("img.png")).toEqual(new Set(["notes/source.md"]));
    expect(index.getAssetsForNote("notes/source.md")).toEqual(
      new Set(["img.png", "docs/diagram.svg"]),
    );
    expect(index.getLinkedNotesForNote("notes/source.md")).toEqual(new Set(["a.md"]));

    const second = index.updateNote("notes/source.md", ["docs/diagram.svg", "b.md"]);
    expect(second.addedAssets.size).toBe(0);
    expect(second.removedAssets).toEqual(new Set(["img.png"]));
    expect(second.addedNotes).toEqual(new Set(["b.md"]));
    expect(second.removedNotes).toEqual(new Set(["a.md"]));

    expect(index.isAssetReferenced("img.png")).toBe(false);
    expect(index.getNotesForAsset("img.png")).toEqual(new Set());
    expect(index.getLinkedNotesForNote("notes/source.md")).toEqual(new Set(["b.md"]));
  });

  test("ignores unresolved references and tolerates duplicates", () => {
    const index = new ReferenceIndex(resolver);
    const diff = index.updateNote("n.md", ["missing.md", "img.png", "img.png", "a.md", "a.md"]);

    expect(diff.addedAssets).toEqual(new Set(["img.png"]));
    expect(diff.addedNotes).toEqual(new Set(["a.md"]));
    expect(index.getAssetsForNote("n.md")).toEqual(new Set(["img.png"]));
    expect(index.getLinkedNotesForNote("n.md")).toEqual(new Set(["a.md"]));
  });

  test("renameNote moves reference mappings", () => {
    const index = new ReferenceIndex(resolver);
    index.updateNote("old.md", ["img.png", "a.md"]);

    index.renameNote("old.md", "new.md");

    expect(index.getAssetsForNote("old.md")).toEqual(new Set());
    expect(index.getAssetsForNote("new.md")).toEqual(new Set(["img.png"]));
    expect(index.getNotesForAsset("img.png")).toEqual(new Set(["new.md"]));
  });

  test("removeNote drops reverse references", () => {
    const index = new ReferenceIndex(resolver);
    index.updateNote("n1.md", ["img.png"]);
    index.updateNote("n2.md", ["img.png"]);

    index.removeNote("n1.md");

    expect(index.getNotesForAsset("img.png")).toEqual(new Set(["n2.md"]));
    expect(index.isAssetReferenced("img.png")).toBe(true);

    index.removeNote("n2.md");

    expect(index.getNotesForAsset("img.png")).toEqual(new Set());
    expect(index.isAssetReferenced("img.png")).toBe(false);
  });
});
