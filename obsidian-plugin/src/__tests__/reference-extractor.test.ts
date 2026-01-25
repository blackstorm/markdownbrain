import { describe, expect, test } from "vitest";
import { extractInternalLinkpathsFromCache } from "../services/reference-extractor";

describe("extractInternalLinkpathsFromCache", () => {
  test("collects linkpaths from links/embeds/frontmatterLinks and strips subpaths", () => {
    const cache = {
      links: [{ link: "notes/todo#Heading", original: "[[todo]]" }],
      embeds: [{ link: "assets/image.png#^block", original: "![[image.png]]" }],
      frontmatterLinks: [{ link: "covers/cover.jpg", id: "cover" }],
    };

    expect(extractInternalLinkpathsFromCache(cache)).toEqual([
      "notes/todo",
      "assets/image.png",
      "covers/cover.jpg",
    ]);
  });

  test("filters external links and empty linkpaths", () => {
    const cache = {
      links: [
        { link: "https://example.com/path", original: "https://example.com/path" },
        { link: "http://example.com/path", original: "http://example.com/path" },
        { link: "file://localhost/path", original: "file://localhost/path" },
        { link: "mailto:test@example.com", original: "mailto:test@example.com" },
        { link: "obsidian://open?vault=X", original: "obsidian://open?vault=X" },
        { link: "#just-a-heading", original: "[[#just-a-heading]]" },
        { link: "", original: "" },
        { link: "local/note", original: "[[local/note]]" },
      ],
    };

    expect(extractInternalLinkpathsFromCache(cache)).toEqual(["local/note"]);
  });

  test("does not treat note names with colon as external links", () => {
    const cache = {
      links: [
        { link: "Project:Plan", original: "[[Project:Plan]]" },
        { link: "folder/Project:Plan", original: "[[folder/Project:Plan]]" },
      ],
    };

    expect(extractInternalLinkpathsFromCache(cache)).toEqual(["Project:Plan", "folder/Project:Plan"]);
  });

  test("deduplicates while preserving first-seen order", () => {
    const cache = {
      links: [
        { link: "dup", original: "[[dup]]" },
        { link: "dup#Heading", original: "[[dup#Heading]]" },
        { link: "unique", original: "[[unique]]" },
        { link: "dup", original: "[[dup]]" },
      ],
    };

    expect(extractInternalLinkpathsFromCache(cache)).toEqual(["dup", "unique"]);
  });
});
