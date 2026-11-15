import { createAudioPlayer, setAudioModeAsync } from 'expo-audio';

/**
 * The NexHome notification sound (`assets/sounds/notify.wav`, the push channels' sound too), played on any screen
 * while the app is open when a new notification or booking change arrives (`components/LiveWatcher`). It lowers other
 * audio for a moment instead of stopping it, and plays at most once in 4 seconds, so one event seen two ways (a push
 * and the next look, a notification and the booking change) sounds once.
 */
const players = {};
let lastPlayed = 0;
const SOUNDS = {
  chime: () => require('../../assets/sounds/notify.wav'),
  doorbell: () => require('../../assets/sounds/doorbell.wav')
};

function play(name) {
  const now = Date.now();
  if (now - lastPlayed < 4000) return;
  lastPlayed = now;
  try {
    if (!players[name]) {
      setAudioModeAsync({ interruptionModeAndroid: 'duckOthers', shouldPlayInBackground: false }).catch(() => {});
      players[name] = createAudioPlayer(SOUNDS[name]());
    }
    players[name].seekTo(0);
    players[name].play();
  } catch {
    // No sound on this phone right now: the notification still shows in the app
  }
}

/** The NexHome notification chime. */
export function chime() {
  play('chime');
}

/** The doorbell (Classic ding-dong, `assets/sounds/doorbell.wav`): the professional arrived at the door. */
export function doorbell() {
  play('doorbell');
}

/** When the chime last played (ms). */
export function lastChimeTime() {
  return lastPlayed;
}
