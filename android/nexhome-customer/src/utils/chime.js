import { createAudioPlayer, setAudioModeAsync } from 'expo-audio';

/**
 * The NexHome notification sound (`assets/sounds/notify.wav`, the push channels' sound too), played on any screen
 * while the app is open when a new notification or booking change arrives (`components/LiveWatcher`). It lowers other
 * audio for a moment instead of stopping it, and plays at most once in 4 seconds, so one event seen two ways (a push
 * and the next look, a notification and the booking change) sounds once.
 */
let player = null;
let lastPlayed = 0;

export function chime() {
  const now = Date.now();
  if (now - lastPlayed < 4000) return;
  lastPlayed = now;
  try {
    if (!player) {
      setAudioModeAsync({ interruptionModeAndroid: 'duckOthers', shouldPlayInBackground: false }).catch(() => {});
      player = createAudioPlayer(require('../../assets/sounds/notify.wav'));
    }
    player.seekTo(0);
    player.play();
  } catch {
    // No sound on this phone right now: the notification still shows in the app
  }
}

/** When the chime last played (ms). */
export function lastChimeTime() {
  return lastPlayed;
}
