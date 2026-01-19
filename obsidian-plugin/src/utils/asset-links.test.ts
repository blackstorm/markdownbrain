import { describe, expect, test } from "vitest";
import { extractAssetPaths } from "./asset-links";

describe("extractAssetPaths", () => {
  test("extracts embed paths and strips aliases/anchors", () => {
    const content = [
      "![[assets/image.png|thumb]]",
      "![[assets/photo.jpg#section]]",
      "![[ 'assets/icon.svg' ]]",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual([
      "assets/image.png",
      "assets/photo.jpg",
      "assets/icon.svg",
    ]);
  });

  test("extracts markdown image paths and trims titles/quotes", () => {
    const content = [
      "![Alt](assets/cover.png)",
      '![Alt]("assets/diagram.svg" "Title")',
      "![Alt]('assets/banner.jpg' 'Title')",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual([
      "assets/cover.png",
      "assets/diagram.svg",
      "assets/banner.jpg",
    ]);
  });

  test("filters out non-asset extensions", () => {
    const content = [
      "![[notes/readme.md]]",
      "![Alt](docs/guide.txt)",
      "![Alt](assets/photo.jpeg)",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual(["assets/photo.jpeg"]);
  });

  test("returns empty array when no asset links exist", () => {
    const content = "Just a note with [[internal link]] and no assets.";

    expect(extractAssetPaths(content)).toEqual([]);
  });
});
