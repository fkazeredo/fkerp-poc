import { NavigationError } from '@angular/router';

const RELOAD_KEY = 'fkerp:chunk-reload-at';
// Don't reload more than once per this window, so a genuinely broken deploy can't loop forever.
const RELOAD_DEBOUNCE_MS = 10_000;

/**
 * Whether a navigation error is a failed lazy-chunk load. After a redeploy the hashed lazy chunks are
 * replaced and the old files removed; a browser tab still running the previous build then fails to fetch a
 * route's chunk (404), which surfaces as a {@code ChunkLoadError} or a "failed to fetch dynamically imported
 * module" error and silently aborts the navigation.
 */
export function isChunkLoadError(error: unknown): boolean {
  if (!error) {
    return false;
  }
  const name = (error as { name?: string }).name ?? '';
  const message = (error as { message?: string }).message ?? String(error);
  return (
    name === 'ChunkLoadError' ||
    /loading chunk \d+ failed/i.test(message) ||
    /dynamically imported module/i.test(message) ||
    /importing a module script failed/i.test(message)
  );
}

/**
 * Reloads the page once (debounced) so the tab fetches the current build. Injectable {@code win}/{@code
 * storage} keep it unit-testable without touching the real {@code window}.
 */
export function maybeReload(
  win: { location: { reload(): void } } = window,
  storage: Pick<Storage, 'getItem' | 'setItem'> = window.sessionStorage,
): void {
  const now = Date.now();
  const last = Number(storage.getItem(RELOAD_KEY) ?? '0');
  if (now - last > RELOAD_DEBOUNCE_MS) {
    storage.setItem(RELOAD_KEY, String(now));
    win.location.reload();
  }
}

/**
 * Router {@code withNavigationErrorHandler} callback: when a navigation fails because a lazy route chunk
 * could not be loaded (typically a tab left open across a redeploy), reload once to pick up the current
 * build instead of silently failing to navigate (the link appears to "do nothing"). Other navigation errors
 * are left untouched.
 */
export function reloadOnChunkError(event: NavigationError): void {
  if (isChunkLoadError(event.error)) {
    maybeReload();
  }
}
