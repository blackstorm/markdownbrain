import { describe, test, expect, beforeEach, afterEach, mock } from 'bun:test';
import { DebounceService, TimerFactory } from '../services/debounce';

describe('DebounceService', () => {
    let service: DebounceService;
    let mockSetTimeout: ReturnType<typeof mock>;
    let mockClearTimeout: ReturnType<typeof mock>;
    let timerIdCounter: number;
    let activeTimers: Map<number, { callback: () => void; delay: number }>;

    beforeEach(() => {
        timerIdCounter = 0;
        activeTimers = new Map();

        mockSetTimeout = mock((callback: () => void, delay: number) => {
            const id = ++timerIdCounter;
            activeTimers.set(id, { callback, delay });
            return id as unknown as ReturnType<typeof setTimeout>;
        });

        mockClearTimeout = mock((id: ReturnType<typeof setTimeout>) => {
            activeTimers.delete(id as unknown as number);
        });

        const timerFactory: TimerFactory = {
            setTimeout: mockSetTimeout,
            clearTimeout: mockClearTimeout,
        };

        service = new DebounceService(timerFactory);
    });

    afterEach(() => {
        service.clearAll();
    });

    describe('debounce', () => {
        test('schedules callback with specified delay', () => {
            const callback = mock(() => {});
            
            service.debounce('test-key', callback, 300);

            expect(mockSetTimeout).toHaveBeenCalledTimes(1);
            expect(mockSetTimeout.mock.calls[0][1]).toBe(300);
        });

        test('clears previous timer when debouncing same key', () => {
            const callback1 = mock(() => {});
            const callback2 = mock(() => {});
            
            service.debounce('test-key', callback1, 300);
            service.debounce('test-key', callback2, 300);

            expect(mockSetTimeout).toHaveBeenCalledTimes(2);
            expect(mockClearTimeout).toHaveBeenCalledTimes(1);
        });

        test('does not clear timer for different keys', () => {
            const callback1 = mock(() => {});
            const callback2 = mock(() => {});
            
            service.debounce('key1', callback1, 300);
            service.debounce('key2', callback2, 300);

            expect(mockSetTimeout).toHaveBeenCalledTimes(2);
            expect(mockClearTimeout).toHaveBeenCalledTimes(0);
        });

        test('removes key from internal map after callback executes', () => {
            const callback = mock(() => {});
            
            service.debounce('test-key', callback, 300);

            // Simulate timer firing
            const timerInfo = activeTimers.get(1);
            timerInfo?.callback();

            // Debouncing same key should not call clearTimeout (no existing timer)
            mockClearTimeout.mockClear();
            service.debounce('test-key', callback, 300);
            expect(mockClearTimeout).toHaveBeenCalledTimes(0);
        });
    });

    describe('cancel', () => {
        test('cancels specific key timer', () => {
            const callback = mock(() => {});
            
            service.debounce('test-key', callback, 300);
            service.cancel('test-key');

            expect(mockClearTimeout).toHaveBeenCalledTimes(1);
        });

        test('does nothing for non-existent key', () => {
            service.cancel('non-existent');

            expect(mockClearTimeout).toHaveBeenCalledTimes(0);
        });
    });

    describe('clearAll', () => {
        test('clears all active timers', () => {
            const callback = mock(() => {});
            
            service.debounce('key1', callback, 300);
            service.debounce('key2', callback, 300);
            service.debounce('key3', callback, 300);

            service.clearAll();

            expect(mockClearTimeout).toHaveBeenCalledTimes(3);
        });

        test('clears internal map after clearAll', () => {
            const callback = mock(() => {});
            
            service.debounce('test-key', callback, 300);
            service.clearAll();

            // Debouncing same key should not call clearTimeout (no existing timer)
            mockClearTimeout.mockClear();
            service.debounce('test-key', callback, 300);
            expect(mockClearTimeout).toHaveBeenCalledTimes(0);
        });
    });

    describe('has', () => {
        test('returns true if key has pending timer', () => {
            const callback = mock(() => {});
            
            service.debounce('test-key', callback, 300);

            expect(service.has('test-key')).toBe(true);
        });

        test('returns false if key has no pending timer', () => {
            expect(service.has('non-existent')).toBe(false);
        });

        test('returns false after timer fires', () => {
            const callback = mock(() => {});
            
            service.debounce('test-key', callback, 300);
            
            // Simulate timer firing
            const timerInfo = activeTimers.get(1);
            timerInfo?.callback();

            expect(service.has('test-key')).toBe(false);
        });
    });
});

describe('DebounceService with real timers', () => {
    let service: DebounceService;

    beforeEach(() => {
        service = new DebounceService(); // Uses default real timers
    });

    afterEach(() => {
        service.clearAll();
    });

    test('executes callback after delay', async () => {
        const callback = mock(() => {});
        
        service.debounce('test-key', callback, 50);

        expect(callback).not.toHaveBeenCalled();
        
        await new Promise(resolve => setTimeout(resolve, 100));
        
        expect(callback).toHaveBeenCalledTimes(1);
    });

    test('only executes last callback when debounced multiple times', async () => {
        let callCount = 0;
        const results: number[] = [];
        
        service.debounce('test-key', () => results.push(1), 50);
        service.debounce('test-key', () => results.push(2), 50);
        service.debounce('test-key', () => results.push(3), 50);

        await new Promise(resolve => setTimeout(resolve, 100));
        
        expect(results).toEqual([3]);
    });
});
