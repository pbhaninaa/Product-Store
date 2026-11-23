import { describe, expect, it, vi } from 'vitest'
import {
  MIN_GAP_MS,
  SOUND_PREF_KEY,
  createAlertSound,
  isInNativeShell,
  isNotificationSoundEnabled,
  setNotificationSoundEnabled,
  shouldPlayAlertSound
} from '@/services/notificationSound'

function memoryStorage(initial = {}) {
  const data = { ...initial }
  return {
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => {
      data[k] = String(v)
    }
  }
}

function fakeAudioContext(state = 'running') {
  const created = []
  class FakeCtx {
    constructor() {
      this.state = state
      this.currentTime = 0
      this.destination = {}
      this.resume = vi.fn(() => {
        this.state = 'running'
        return Promise.resolve()
      })
      created.push(this)
    }
    createOscillator() {
      const osc = { type: '', frequency: { setValueAtTime: vi.fn() }, connect: vi.fn(), start: vi.fn(), stop: vi.fn() }
      ;(this.oscillators = this.oscillators || []).push(osc)
      return osc
    }
    createGain() {
      return { gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() }, connect: vi.fn() }
    }
  }
  return { FakeCtx, created }
}

const unread = { id: 'n1', title: 'New booking', read: false }

describe('sound preference', () => {
  it('is on by default and can be muted and unmuted', () => {
    const s = memoryStorage()
    expect(isNotificationSoundEnabled(s)).toBe(true)
    setNotificationSoundEnabled(false, s)
    expect(s.getItem(SOUND_PREF_KEY)).toBe('off')
    expect(isNotificationSoundEnabled(s)).toBe(false)
    setNotificationSoundEnabled(true, s)
    expect(isNotificationSoundEnabled(s)).toBe(true)
  })

  it('stays on when storage is blocked', () => {
    const blocked = { getItem: () => { throw new Error('denied') }, setItem: () => { throw new Error('denied') } }
    expect(isNotificationSoundEnabled(blocked)).toBe(true)
    expect(() => setNotificationSoundEnabled(false, blocked)).not.toThrow()
  })
})

describe('isInNativeShell', () => {
  it('detects the React Native WebView bridge', () => {
    expect(isInNativeShell({ ReactNativeWebView: { postMessage: () => {} } })).toBe(true)
    expect(isInNativeShell({})).toBe(false)
    expect(isInNativeShell(undefined)).toBe(false)
  })
})

describe('shouldPlayAlertSound', () => {
  const base = { enabled: true, inNativeShell: false, now: 10000, lastPlayedAt: null }

  it('plays for a new unread notification', () => {
    expect(shouldPlayAlertSound(unread, base)).toBe(true)
  })

  it('stays silent for read, empty, muted or native-shell cases', () => {
    expect(shouldPlayAlertSound({ ...unread, read: true }, base)).toBe(false)
    expect(shouldPlayAlertSound(null, base)).toBe(false)
    expect(shouldPlayAlertSound({ title: 'x' }, base)).toBe(false)
    expect(shouldPlayAlertSound(unread, { ...base, enabled: false })).toBe(false)
    expect(shouldPlayAlertSound(unread, { ...base, inNativeShell: true })).toBe(false)
  })

  it('plays once per burst', () => {
    expect(shouldPlayAlertSound(unread, { ...base, lastPlayedAt: base.now - MIN_GAP_MS + 1 })).toBe(false)
    expect(shouldPlayAlertSound(unread, { ...base, lastPlayedAt: base.now - MIN_GAP_MS })).toBe(true)
  })
})

describe('createAlertSound', () => {
  it('plays a two-note tone and throttles a burst', () => {
    const { FakeCtx, created } = fakeAudioContext('running')
    let t = 0
    const sound = createAlertSound({ win: {}, AudioContextImpl: FakeCtx, storage: memoryStorage(), now: () => t })

    expect(sound.notify(unread)).toBe(true)
    expect(created).toHaveLength(1)
    expect(created[0].oscillators).toHaveLength(2)

    t = 500
    expect(sound.notify({ ...unread, id: 'n2' })).toBe(false)
    t = 500 + MIN_GAP_MS
    expect(sound.notify({ ...unread, id: 'n3' })).toBe(true)
  })

  it('is silent until a user gesture unlocks a suspended context', async () => {
    const { FakeCtx, created } = fakeAudioContext('suspended')
    const sound = createAlertSound({ win: {}, AudioContextImpl: FakeCtx, storage: memoryStorage(), now: () => 0 })

    expect(sound.notify(unread)).toBe(false)
    sound.unlock()
    await Promise.resolve()
    expect(created[0].resume).toHaveBeenCalled()
    expect(sound.notify({ ...unread, id: 'n2' })).toBe(true)
  })

  it('stays silent inside the phone app and when muted', () => {
    const { FakeCtx, created } = fakeAudioContext('running')
    const shell = createAlertSound({
      win: { ReactNativeWebView: { postMessage: () => {} } },
      AudioContextImpl: FakeCtx,
      storage: memoryStorage(),
      now: () => 0
    })
    expect(shell.notify(unread)).toBe(false)

    const muted = createAlertSound({
      win: {},
      AudioContextImpl: FakeCtx,
      storage: memoryStorage({ [SOUND_PREF_KEY]: 'off' }),
      now: () => 0
    })
    expect(muted.notify(unread)).toBe(false)
    expect(created).toHaveLength(0)
  })

  it('does nothing when the browser has no Web Audio', () => {
    const sound = createAlertSound({ win: {}, AudioContextImpl: undefined, storage: memoryStorage(), now: () => 0 })
    expect(() => sound.unlock()).not.toThrow()
    expect(sound.notify(unread)).toBe(false)
  })
})
