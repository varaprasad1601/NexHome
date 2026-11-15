import { createAudioPlayer, setAudioModeAsync } from 'expo-audio';

/**
 * The NexHome notification sound (`assets/sounds/notify.wav`, the push channels' sound too), played while the app is
 * open when a new notification arrives. It lowers other audio for a moment instead of stopping it.
 */
let player = null;

export function chime() {
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
