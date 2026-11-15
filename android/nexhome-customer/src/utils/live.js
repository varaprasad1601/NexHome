import { useSyncExternalStore } from 'react';

/**
 * Live updates without push to the screens: `components/LiveWatcher` (rendered once in _layout) looks at the
 * notifications (and, in the customer app, the bookings) and calls `bump(topic)` when something changed; screens
 * reload through `useApi(path, { live: topic })` while they are focused. Topics: 'notifications' (a new notification
 * arrived) and 'bookings' (a booking changed: the professional accepted, started, finished, staff confirmed, ...).
 * `unread` is the latest unread count, for the bell.
 */
const listeners = new Map();
let unread = null;
const unreadListeners = new Set();

/** Calls `callback` whenever `topic` is bumped; returns the unsubscribe function. */
export function onLive(topic, callback) {
  if (!listeners.has(topic)) listeners.set(topic, new Set());
  listeners.get(topic).add(callback);
  return () => listeners.get(topic)?.delete(callback);
}

/** Something changed for `topic`: every focused screen listening to it reloads. */
export function bump(...topics) {
  for (const topic of topics) {
    for (const callback of [...(listeners.get(topic) ?? [])]) {
      try {
        callback();
      } catch {
        // One screen failing never stops the others
      }
    }
  }
}

export function setUnread(count) {
  if (count === unread) return;
  unread = count;
  unreadListeners.forEach((listener) => listener());
}

/** The latest unread count the watcher saw (null before the first look). */
export function useUnread() {
  return useSyncExternalStore(
    (listener) => {
      unreadListeners.add(listener);
      return () => unreadListeners.delete(listener);
    },
    () => unread
  );
}
