import { describe, expect, it, vi } from 'vitest'
import { ReconnectionTimeMode } from '@stomp/stompjs'
import {
  FALLBACK_POLL_MS,
  NOTIFICATIONS_DESTINATION,
  RECONNECT_INITIAL_MS,
  RECONNECT_MAX_MS,
  belongsToInbox,
  createNotificationsRealtime,
  notificationNavPath,
  postToNativeShell,
  prependNotification,
  socketUrl
} from '@/services/notificationsRealtime'

describe('socketUrl', () => {
  it('maps an https API to wss on the native WebSocket endpoint with the token', () => {
    expect(socketUrl('https://api.example.com', 'https://app.example.com/x', 'a b')).toBe(
      'wss://api.example.com/ws/websocket?access_token=a%20b'
    )
  })

  it('maps http to ws and strips a trailing /api', () => {
    expect(socketUrl('http://localhost:8080/api/', 'http://localhost:8085/', 't')).toBe(
      'ws://localhost:8080/ws/websocket?access_token=t'
    )
  })

  it('never opens an insecure socket from an https page', () => {
    expect(socketUrl('http://api.example.com', 'https://app.example.com/', 't')).toBe(
      'wss://api.example.com/ws/websocket?access_token=t'
    )
  })

  it('uses the page origin for a loopback http API under an https page (dev proxy)', () => {
    expect(socketUrl('http://localhost:8080', 'https://dev.local:8443/', 't')).toBe(
      'wss://dev.local:8443/ws/websocket?access_token=t'
    )
  })

  it('falls back to the page origin when no API base is configured', () => {
    expect(socketUrl('', 'https://app.example.com/m/x', 't')).toBe('wss://app.example.com/ws/websocket?access_token=t')
  })
})

describe('inbox helpers', () => {
  it('matches a store inbox by slug (case-insensitive) and the platform inbox by missing tenant', () => {
    expect(belongsToInbox({ tenantId: 'x', tenantSlug: 'Shop' }, 'shop')).toBe(true)
    expect(belongsToInbox({ tenantId: 'x', tenantSlug: 'other' }, 'shop')).toBe(false)
    expect(belongsToInbox({ tenantId: null, tenantSlug: null }, '')).toBe(true)
    expect(belongsToInbox({ tenantId: 'x', tenantSlug: 'shop' }, '')).toBe(false)
    expect(belongsToInbox(null, 'shop')).toBe(false)
  })

  it('prepends the REST-shaped item and drops a duplicate id', () => {
    const list = [{ id: '1', title: 'old' }, { id: '2', title: 'two' }]
    const next = prependNotification(list, { id: '1', title: 'new', read: false, unread: 3, tenantSlug: 's' })
    expect(next.map((n) => n.id)).toEqual(['1', '2'])
    expect(next[0].title).toBe('new')
    expect(next[0]).not.toHaveProperty('unread')
    expect(list[0].title).toBe('old')
  })

  it('routes the phone notification to the right inbox', () => {
    expect(notificationNavPath({ tenantSlug: 'my shop' })).toBe('/m/my%20shop/admin/notifications')
    expect(notificationNavPath({ tenantSlug: null })).toBe('/support/notifications')
  })
})

describe('postToNativeShell', () => {
  it('posts SHOW_PUSH_NOTIFICATION and NAV_BADGES inside the WebView shell', () => {
    const postMessage = vi.fn()
    const ok = postToNativeShell(
      { id: 'n1', title: 'New order', body: 'R50', tenantSlug: 'shop', unread: 4 },
      { ReactNativeWebView: { postMessage } }
    )
    expect(ok).toBe(true)
    expect(JSON.parse(postMessage.mock.calls[0][0])).toEqual({
      type: 'SHOW_PUSH_NOTIFICATION',
      id: 'n1',
      title: 'New order',
      body: 'R50',
      navPath: '/m/shop/admin/notifications'
    })
    expect(JSON.parse(postMessage.mock.calls[1][0])).toEqual({ type: 'NAV_BADGES', navBadges: { notifications: 4 } })
  })

  it('does nothing in a normal browser', () => {
    expect(postToNativeShell({ id: 'n1', title: 't' }, {})).toBe(false)
  })
})

/** Minimal stand-in for @stomp/stompjs Client. */
function fakeClientFactory() {
  const created = []
  class FakeClient {
    constructor(config) {
      Object.assign(this, config)
      this.config = config
      this.activate = vi.fn()
      this.deactivate = vi.fn()
      this.subscribe = vi.fn((dest, cb) => {
        this.subscription = { dest, cb }
      })
      created.push(this)
    }
  }
  return { FakeClient, created }
}

function setup(initialToken = 'tok1') {
  let token = initialToken
  const { FakeClient, created } = fakeClientFactory()
  const timers = []
  const onNotification = vi.fn()
  const onResync = vi.fn()
  const postToNative = vi.fn()
  const rt = createNotificationsRealtime({
    getToken: () => token,
    onNotification,
    onResync,
    apiBase: () => 'https://api.example.com',
    pageHref: () => 'https://app.example.com/',
    ClientImpl: FakeClient,
    postToNative,
    setIntervalFn: (fn, ms) => {
      timers.push({ fn, ms, cleared: false })
      return timers.length - 1
    },
    clearIntervalFn: (id) => {
      timers[id].cleared = true
    }
  })
  return { rt, created, timers, onNotification, onResync, postToNative, setToken: (t) => (token = t) }
}

describe('createNotificationsRealtime', () => {
  it('connects once per token with exponential reconnect backoff', () => {
    const { rt, created } = setup()
    rt.sync()
    rt.sync() // auth tick with the same token: no reconnect
    expect(created).toHaveLength(1)
    const c = created[0]
    expect(c.activate).toHaveBeenCalledTimes(1)
    expect(c.brokerURL).toBe('wss://api.example.com/ws/websocket?access_token=tok1')
    expect(c.config.reconnectTimeMode).toBe(ReconnectionTimeMode.EXPONENTIAL)
    expect(c.config.reconnectDelay).toBe(RECONNECT_INITIAL_MS)
    expect(c.config.maxReconnectDelay).toBe(RECONNECT_MAX_MS)
  })

  it('re-authenticates each reconnect with the current token', () => {
    const { rt, created, setToken } = setup()
    rt.sync()
    const c = created[0]
    setToken('tok2')
    c.beforeConnect()
    expect(c.brokerURL).toBe('wss://api.example.com/ws/websocket?access_token=tok2')
    setToken('')
    c.beforeConnect()
    expect(c.deactivate).toHaveBeenCalled()
  })

  it('reconnects on token change and disconnects on sign-out', () => {
    const { rt, created, timers, setToken } = setup()
    rt.sync()
    setToken('tok2')
    rt.sync()
    expect(created).toHaveLength(2)
    expect(created[0].deactivate).toHaveBeenCalled()
    expect(timers[0].cleared).toBe(true)
    setToken('')
    rt.sync()
    expect(created[1].deactivate).toHaveBeenCalled()
    expect(rt.isConnected()).toBe(false)
  })

  it('does not connect while signed out', () => {
    const { rt, created } = setup('')
    rt.sync()
    expect(created).toHaveLength(0)
  })

  it('subscribes on connect and dispatches each pushed notification (and to the native shell)', () => {
    const { rt, created, onNotification, postToNative } = setup()
    rt.sync()
    const c = created[0]
    c.onConnect()
    expect(rt.isConnected()).toBe(true)
    expect(c.subscription.dest).toBe(NOTIFICATIONS_DESTINATION)
    const payload = { id: 'n1', title: 'New order', tenantSlug: 'shop', unread: 2 }
    c.subscription.cb({ body: JSON.stringify(payload) })
    expect(onNotification).toHaveBeenCalledWith(payload)
    expect(postToNative).toHaveBeenCalledWith(payload)
    c.subscription.cb({ body: 'not json' })
    expect(onNotification).toHaveBeenCalledTimes(1)
  })

  it('resyncs after a reconnect but not on the first connect', () => {
    const { rt, created, onResync } = setup()
    rt.sync()
    const c = created[0]
    c.onConnect()
    expect(onResync).not.toHaveBeenCalled()
    c.onWebSocketClose()
    expect(rt.isConnected()).toBe(false)
    c.onConnect()
    expect(onResync).toHaveBeenCalledTimes(1)
  })

  it('falls back to a slow resync tick only while disconnected', () => {
    const { rt, created, timers, onResync } = setup()
    rt.sync()
    expect(timers[0].ms).toBe(FALLBACK_POLL_MS)
    expect(FALLBACK_POLL_MS).toBeGreaterThanOrEqual(2 * 60 * 1000)
    timers[0].fn() // never connected yet
    expect(onResync).toHaveBeenCalledTimes(1)
    created[0].onConnect()
    timers[0].fn() // connected: no poll
    expect(onResync).toHaveBeenCalledTimes(1)
  })
})
