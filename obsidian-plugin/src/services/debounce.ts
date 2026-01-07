/**
 * Timer factory interface for dependency injection
 * Allows testing with mock timers
 */
export interface TimerFactory {
    setTimeout: (callback: () => void, delay: number) => ReturnType<typeof setTimeout>;
    clearTimeout: (id: ReturnType<typeof setTimeout>) => void;
}

/**
 * Default timer factory using global setTimeout/clearTimeout
 */
export const defaultTimerFactory: TimerFactory = {
    setTimeout: (callback, delay) => setTimeout(callback, delay),
    clearTimeout: (id) => clearTimeout(id),
};

/**
 * Debounce service for managing multiple debounced operations by key
 * 
 * @example
 * ```ts
 * const debounce = new DebounceService();
 * 
 * // Debounce file sync by path
 * debounce.debounce(file.path, () => syncFile(file), 300);
 * 
 * // Clear all on unload
 * debounce.clearAll();
 * ```
 */
export class DebounceService {
    private timers: Map<string, ReturnType<typeof setTimeout>>;
    private timerFactory: TimerFactory;

    constructor(timerFactory: TimerFactory = defaultTimerFactory) {
        this.timers = new Map();
        this.timerFactory = timerFactory;
    }

    /**
     * Schedule a debounced callback for the given key
     * If a timer already exists for the key, it will be cancelled and replaced
     */
    debounce(key: string, callback: () => void, delay: number): void {
        // Cancel existing timer for this key
        const existingTimer = this.timers.get(key);
        if (existingTimer !== undefined) {
            this.timerFactory.clearTimeout(existingTimer);
        }

        // Schedule new timer
        const timer = this.timerFactory.setTimeout(() => {
            this.timers.delete(key);
            callback();
        }, delay);

        this.timers.set(key, timer);
    }

    /**
     * Cancel a specific debounced operation by key
     */
    cancel(key: string): void {
        const timer = this.timers.get(key);
        if (timer !== undefined) {
            this.timerFactory.clearTimeout(timer);
            this.timers.delete(key);
        }
    }

    /**
     * Clear all pending timers
     */
    clearAll(): void {
        for (const timer of this.timers.values()) {
            this.timerFactory.clearTimeout(timer);
        }
        this.timers.clear();
    }

    /**
     * Check if a key has a pending timer
     */
    has(key: string): boolean {
        return this.timers.has(key);
    }
}
