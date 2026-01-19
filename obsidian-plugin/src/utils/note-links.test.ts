import { describe, expect, test } from "vitest";
import { extractNotePaths } from "./asset-links";

describe("extractNotePaths", () => {
  test("extracts wiki links and strips section anchors", () => {
    const input = "Link to [[Notes/Alpha#Heading|Alias]] and ![[Beta]]";
    expect(extractNotePaths(input)).toEqual(["Notes/Alpha", "Beta"]);
  });

  test("extracts markdown links and ignores images", () => {
    const input = "See [Doc](docs/Guide.md) and ![Img](assets/a.png)";
    expect(extractNotePaths(input)).toEqual(["docs/Guide.md"]);
  });

  test("ignores external links", () => {
    const input = "External [site](https://example.com)";
    expect(extractNotePaths(input)).toEqual([]);
  });

  test("ignores code blocks and inline code", () => {
    const input = "Code `[[Inline]]`\n```\n[[Block]]\n```\n[[Real]]";
    expect(extractNotePaths(input)).toEqual(["Real"]);
  });
});
