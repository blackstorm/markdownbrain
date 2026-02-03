import { Notice } from "obsidian";
import type { NoticePort } from "../domain/types";

export class ObsidianNoticeAdapter implements NoticePort {
  show(message: string, timeoutMs?: number): void {
    new Notice(message, timeoutMs);
  }
}
