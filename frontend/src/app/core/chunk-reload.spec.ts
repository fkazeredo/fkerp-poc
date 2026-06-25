import { NavigationError } from '@angular/router';
import { isChunkLoadError, maybeReload, reloadOnChunkError } from './chunk-reload';

describe('chunk-reload', () => {
  describe('isChunkLoadError', () => {
    it('detects a ChunkLoadError by name', () => {
      const err = new Error('boom');
      err.name = 'ChunkLoadError';
      expect(isChunkLoadError(err)).toBe(true);
    });

    it('detects the "loading chunk N failed" message', () => {
      expect(isChunkLoadError(new Error('Loading chunk 42 failed.'))).toBe(true);
    });

    it('detects a failed dynamic import message', () => {
      expect(
        isChunkLoadError(new Error('Failed to fetch dynamically imported module: /chunk-ABC.js')),
      ).toBe(true);
      expect(isChunkLoadError(new TypeError('error loading dynamically imported module'))).toBe(true);
      expect(isChunkLoadError(new Error('Importing a module script failed.'))).toBe(true);
    });

    it('ignores unrelated errors and nullish values', () => {
      expect(isChunkLoadError(new Error('Something else'))).toBe(false);
      expect(isChunkLoadError(null)).toBe(false);
      expect(isChunkLoadError(undefined)).toBe(false);
    });
  });

  describe('maybeReload', () => {
    function fakeStorage(initial: Record<string, string> = {}) {
      const map = new Map(Object.entries(initial));
      return {
        getItem: (k: string) => map.get(k) ?? null,
        setItem: (k: string, v: string) => void map.set(k, v),
      };
    }

    it('reloads once and records the timestamp when none is stored', () => {
      const reload = vi.fn();
      const storage = fakeStorage();
      maybeReload({ location: { reload } }, storage);
      expect(reload).toHaveBeenCalledTimes(1);
      expect(storage.getItem('fkerp:chunk-reload-at')).not.toBeNull();
    });

    it('does not reload again within the debounce window (avoids a loop)', () => {
      const reload = vi.fn();
      const storage = fakeStorage({ 'fkerp:chunk-reload-at': String(Date.now()) });
      maybeReload({ location: { reload } }, storage);
      expect(reload).not.toHaveBeenCalled();
    });

    it('reloads again once the debounce window has passed', () => {
      const reload = vi.fn();
      const storage = fakeStorage({ 'fkerp:chunk-reload-at': String(Date.now() - 60_000) });
      maybeReload({ location: { reload } }, storage);
      expect(reload).toHaveBeenCalledTimes(1);
    });
  });

  describe('reloadOnChunkError', () => {
    it('does nothing for a non-chunk navigation error', () => {
      // A plain error must not trigger a reload (window untouched).
      const event = { error: new Error('guard rejected') } as NavigationError;
      expect(() => reloadOnChunkError(event)).not.toThrow();
    });
  });
});
