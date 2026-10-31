/**
 * Desktop alerts for new notifications: the browser's system popup (Notification API) and a short chime (Web Audio,
 * no sound file). Each staff member's choices (popups, sound) are kept in this browser only.
 */

const PREFS_KEY = 'nexhome.notificationAlerts';
const DEFAULT_PREFS = { popups: true, sound: true };

/** The saved choices ({ popups, sound }); defaults when nothing is saved or storage is unavailable. */
export function loadAlertPrefs() {
  try {
    return { ...DEFAULT_PREFS, ...JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}') };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

export function saveAlertPrefs(prefs) {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
  } catch {
    // Private windows may refuse storage: the choice then lasts for this page only
  }
}

/** 'granted', 'denied', 'default', or 'unsupported' when the browser has no system notifications. */
export function popupPermission() {
  return typeof window !== 'undefined' && 'Notification' in window ? Notification.permission : 'unsupported';
}

/** Asks the browser for permission (call it from a click); resolves to the new permission. */
export async function requestPopupPermission() {
  if (popupPermission() === 'unsupported') return 'unsupported';
  try {
    return await Notification.requestPermission();
  } catch {
    return popupPermission();
  }
}

/** How long a system popup stays on screen. */
const POPUP_MS = 10000;

let audioContext = null;

/**
 * Browsers only let a page play sound after the user has interacted with it: the first click or key press anywhere
 * prepares the audio, so later chimes play even while the tab is in the background.
 */
export function unlockAudioOnFirstGesture() {
  const unlock = () => {
    try {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (AudioContextClass && !audioContext) audioContext = new AudioContextClass();
      audioContext?.resume();
    } catch {
      // No audio available
    }
    window.removeEventListener('pointerdown', unlock);
    window.removeEventListener('keydown', unlock);
  };
  window.addEventListener('pointerdown', unlock);
  window.addEventListener('keydown', unlock);
  return () => {
    window.removeEventListener('pointerdown', unlock);
    window.removeEventListener('keydown', unlock);
  };
}

/** One soft bell-like tone starting `delay` seconds from now. */
function tone(frequency, delay, duration, volume) {
  const start = audioContext.currentTime + delay;
  const oscillator = audioContext.createOscillator();
  const gain = audioContext.createGain();
  oscillator.type = 'sine';
  oscillator.frequency.setValueAtTime(frequency, start);
  gain.gain.setValueAtTime(0.0001, start);
  gain.gain.exponentialRampToValueAtTime(volume, start + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
  oscillator.connect(gain).connect(audioContext.destination);
  oscillator.start(start);
  oscillator.stop(start + duration + 0.05);
}

/**
 * Gets the audio ready: once the page has had a click or key press (also before a reload by the dev server, which
 * keeps the page's activation) it can be created or woken without another gesture.
 */
function readyAudio() {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!audioContext && AudioContextClass && navigator.userActivation?.hasBeenActive) audioContext = new AudioContextClass();
    if (audioContext?.state === 'suspended') audioContext.resume();
  } catch {
    // No audio available
  }
  return audioContext;
}

/** One chime per notification: two soft notes, or for HIGH three rising, louder notes (played once, not repeated). */
export function playChime(priority = 'NORMAL') {
  if (!readyAudio()) return;
  if (priority === 'HIGH') {
    tone(880, 0, 0.3, 0.3);
    tone(1108.7, 0.12, 0.3, 0.3);
    tone(1318.5, 0.24, 0.5, 0.3);
    return;
  }
  tone(880, 0, 0.35, 0.2);
  tone(1318.5, 0.14, 0.45, 0.2);
}

/**
 * The system popup of one notification. `onOpen` runs when it is clicked (after the console's window is brought
 * forward). Popups of the same notification replace each other (`tag`).
 */
export function showSystemPopup(notification, onOpen) {
  if (popupPermission() !== 'granted') return;
  try {
    const popup = new Notification(`${notification.priority === 'HIGH' ? 'High: ' : ''}${notification.title}`, {
      body: notification.message,
      tag: `nexhome-${notification.notificationId}`,
      icon: '/assets/NexHome_Logo.png'
    });
    // Gone after a few seconds, like the console's own card (the bell keeps the notification)
    setTimeout(() => popup.close(), POPUP_MS);
    popup.onclick = () => {
      window.focus();
      popup.close();
      onOpen(notification);
    };
  } catch {
    // Some browsers only allow popups from a service worker; the bell still shows the notification
  }
}
