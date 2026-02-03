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
      '![Alt](<assets/space image.png> "Title")',
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual([
      "assets/cover.png",
      "assets/diagram.svg",
      "assets/banner.jpg",
      "assets/space image.png",
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

  test("extracts non-embedded wiki links and strips sections", () => {
    const content = [
      "[[assets/photo.png]]",
      "[[assets/photo.png|alias]]",
      "[[assets/photo.png#section]]",
      "[[notes/readme.md]]",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual([
      "assets/photo.png",
      "assets/photo.png",
      "assets/photo.png",
    ]);
  });

  test("extracts reference-style images", () => {
    const content = [
      "![Alt][ref-a]",
      "![Alt][]",
      "",
      '[ref-a]: assets/ref.png "Title"',
      "[Alt]: assets/alt.png",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual(["assets/ref.png", "assets/alt.png"]);
  });

  test("extracts HTML media tags", () => {
    const content = [
      '<img src="assets/html.png" alt="demo" />',
      "<audio src='assets/sound.mp3'></audio>",
      "<video><source src=assets/video.mp4 type=video/mp4></video>",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual([
      "assets/html.png",
      "assets/sound.mp3",
      "assets/video.mp4",
    ]);
  });

  test("strips fragments and queries for markdown images", () => {
    const content = [
      "![Alt](assets/frag.png#section)",
      "![Alt](assets/query.png?raw=1)",
      "![Alt](assets/both.png?raw=1#section)",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual([
      "assets/frag.png",
      "assets/query.png",
      "assets/both.png",
    ]);
  });

  test("ignores links inside code blocks and inline code", () => {
    const content = [
      "Here is `![[assets/inline.png]]` inline.",
      "```md",
      "![[assets/fenced.png]]",
      "![Alt](assets/fenced.jpg)",
      "```",
      "![Alt](assets/real.png)",
    ].join("\n");

    expect(extractAssetPaths(content)).toEqual(["assets/real.png"]);
  });
});
