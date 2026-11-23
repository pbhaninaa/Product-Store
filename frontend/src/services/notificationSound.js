/**
 * Short chat-style "pop" when a realtime notification arrives (like a messaging app's incoming tone).
 *
 * The tone is synthesised with the Web Audio API, so there is no audio asset to ship or license.
 * Browsers block audio until the user has interacted with the page, so the AudioContext is created
 * (or resumed) on the first pointer/key gesture; a notification that lands before any gesture is
 * silent. Inside the React Native shell the phone notification already plays the system sound, so
 * the page stays quiet to avoid a double sound. The viewer can mute it (stored per browser).
 */

export const SOUND_PREF_KEY = 'ps_notification_sound'
/** A burst of notifications (e.g. a batch of bookings) plays one tone, not one per item. */
export const MIN_GAP_MS = 1500

function defaultStorage() {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null
  } catch {
    return null
  }
}

/** Sound is on unless the viewer muted it. */
export function isNotificationSoundEnabled(storage = defaultStorage()) {
  try {
    return !storage || storage.getItem(SOUND_PREF_KEY) !== 'off'
  } catch {
    return true
  }
}

export function setNotificationSoundEnabled(enabled, storage = defaultStorage()) {
  try {
    if (storage) storage.setItem(SOUND_PREF_KEY, enabled ? 'on' : 'off')
  } catch {
    // preference is a convenience; ignore blocked storage
  }
}

export function isInNativeShell(win = typeof window !== 'undefined' ? window : undefined) {
  return Boolean(win && win.ReactNativeWebView && typeof win.ReactNativeWebView.postMessage === 'function')
}

/** Whether a pushed notification should make a sound right now. */
export function shouldPlayAlertSound(payload, { enabled, inNativeShell, now, lastPlayedAt }) {
  if (!payload || !payload.id || payload.read) return false
  if (!enabled || inNativeShell) return false
  return lastPlayedAt == null || now - lastPlayedAt >= MIN_GAP_MS
}

/** Two quick rising sine notes with a fast decay: a soft "pop". */
function playTone(ctx) {
  const start = ctx.currentTime + 0.01
  const notes = [
    { freq: 830, at: 0 },
    { freq: 1245, at: 0.09 }
  ]
  notes.forEach(({ freq, at }) => {
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    const t = start + at
    osc.type = 'sine'
    osc.frequency.setValueAtTime(freq, t)
    gain.gain.setValueAtTime(0.0001, t)
    gain.gain.exponentialRampToValueAtTime(0.25, t + 0.012)
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.16)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start(t)
    osc.stop(t + 0.18)
  })
}

/**
 * Builds the alert-sound player. Dependencies are injectable for tests.
 */
export function createAlertSound({
  win = typeof window !== 'undefined' ? window : undefined,
  AudioContextImpl = win && (win.AudioContext || win.webkitAudioContext),
  storage = defaultStorage(),
  now = () => Date.now()
} = {}) {
  let ctx = null
  let lastPlayedAt = null

  function context() {
    if (!ctx && AudioContextImpl) {
      try {
        ctx = new AudioContextImpl()
      } catch {
        ctx = null
      }
    }
    return ctx
  }

  /** Call from a user gesture so later notifications are allowed to play. */
  function unlock() {
    const c = context()
    if (c && c.state === 'suspended' && typeof c.resume === 'function') {
      c.resume().catch(() => {})
    }
  }

  /** Plays the tone for a pushed notification when allowed; returns whether it played. */
  function notify(payload) {
    const t = now()
    const ok = shouldPlayAlertSound(payload, {
      enabled: isNotificationSoundEnabled(storage),
      inNativeShell: isInNativeShell(win),
      now: t,
      lastPlayedAt
    })
    if (!ok) return false
    const c = context()
    if (!c || c.state !== 'running') return false
    try {
      playTone(c)
      lastPlayedAt = t
      return true
    } catch {
      return false
    }
  }

  return { unlock, notify }
}

// ---------------------------------------------------------------- app singleton

let instance = null
const GESTURES = ['pointerdown', 'keydown', 'touchstart']

/** Installs gesture listeners that unlock audio; safe to call more than once. */
export function installNotificationSound() {
  if (instance || typeof window === 'undefined') return
  instance = createAlertSound()
  const onGesture = () => {
    instance.unlock()
    GESTURES.forEach((g) => window.removeEventListener(g, onGesture, true))
  }
  GESTURES.forEach((g) => window.addEventListener(g, onGesture, true))
}

export function playNotificationSound(payload) {
  return Boolean(instance && instance.notify(payload))
}
