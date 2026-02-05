import type { LoggerPort } from "../domain/types";

const PREFIX = "[Mdbrain]";

export class ConsoleLoggerAdapter implements LoggerPort {
  info(message: string, data?: Record<string, unknown>): void {
    if (data) {
      console.log(`${PREFIX} ${message}`, data);
    } else {
      console.log(`${PREFIX} ${message}`);
    }
  }

  warn(message: string, data?: Record<string, unknown>): void {
    if (data) {
      console.warn(`${PREFIX} ${message}`, data);
    } else {
      console.warn(`${PREFIX} ${message}`);
    }
  }

  error(message: string, data?: Record<string, unknown>): void {
    if (data) {
      console.error(`${PREFIX} ${message}`, data);
    } else {
      console.error(`${PREFIX} ${message}`);
    }
  }

  debug(message: string, data?: Record<string, unknown>): void {
    if (data) {
      console.log(`${PREFIX} [DEBUG] ${message}`, data);
    } else {
      console.log(`${PREFIX} [DEBUG] ${message}`);
    }
  }
}
