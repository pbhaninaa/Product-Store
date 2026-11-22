import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { getApiBase } from '@/services/api'

/**
 * Realtime in-app notifications: one STOMP connection per signed-in session, over the native
 * WebSocket the backend exposes at {@code /ws/websocket} (SockJS registration), authenticated with
 * the JWT in the {@code access_token} query parameter (browsers cannot set headers on WebSocket).
 *
 * Every committed notification arrives on {@code /user/queue/notifications} as the REST list item
 * plus {@code tenantId}, {@code tenantSlug} (null for the support/platform inbox) and {@code unread}.
 * While the socket is down a slow fallback tick asks views to resync; on reconnect they resync once
 * to pick up anything missed.
 */

export const NOTIFICATIONS_DESTINATION = '/user/queue/notifications'
export const RECONNECT_INITIAL_MS = 1000
export const RECONNECT_MAX_MS = 60 * 1000
export const FALLBACK_POLL_MS = 3 * 60 * 1000

function isLoopbackHost(hostname) {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]'
}

/**
 * WebSocket URL for the backend STOMP endpoint, derived from the API base the SPA already uses.
 * http→ws, https→wss; an https page never opens an insecure socket (mixed content) except to a
 * loopback API, where the page's own origin (dev proxy) is used instead.
 */
export function socketUrl(apiBase, pageHref, token) {
  const page = new URL(pageHref)
  let origin = `${page.protocol}//${page.host}`
  const base = String(apiBase || '').trim().replace(/\/+$/, '').replace(/\/api$/i, '')
  if (base) {
    try {
      const api = new URL(/^https?:\/\//i.test(base) ? base : `${page.protocol}//${base}`)
      if (page.protocol === 'https:' && api.protocol === 'http:') {
        origin = isLoopbackHost(api.hostname) ? origin : `https://${api.host}`
      } else {
        origin = `${api.protocol}//${api.host}`
      }
    } catch {
      // keep the page origin
    }
  }
  const ws = origin.replace(/^http/i, 'ws')
  return `${ws}/ws/websocket?access_token=${encodeURIComponent(token)}`
}

/**
 * Whether a pushed notification belongs to the inbox being shown: a store's inbox (by slug), or the
 * support/platform inbox ({@code merchantSlug} empty, payload without a tenant).
 */
export function belongsToInbox(payload, merchantSlug) {
  if (!payload) return false
  const slug = String(merchantSlug || '').trim().toLowerCase()
  const tenantSlug = String(payload.tenantSlug || '').trim().toLowerCase()
  if (!slug) return !payload.tenantId
  return tenantSlug === slug
}

/** New list with the pushed notification (REST list fields only) first; duplicates by id are dropped. */
export function prependNotification(items, payload) {
  const list = Array.isArray(items) ? items : []
  if (!payload || !payload.id) return list
  const item = {
    id: payload.id,
    title: payload.title,
    body: payload.body,
    notificationType: payload.notificationType,
    referenceType: payload.referenceType,
    referenceId: payload.referenceId,
    read: Boolean(payload.read),
    createdAt: payload.createdAt
  }
  return [item, ...list.filter((n) => !n || n.id !== payload.id)]
}

/** Web path the native shell opens when the user taps the phone notification. */
export function notificationNavPath(payload) {
  const slug = payload && payload.tenantSlug
  return slug ? `/m/${encodeURIComponent(slug)}/admin/notifications` : '/support/notifications'
}

/**
 * Inside the React Native WebView shell, hand the notification to the native side so the phone
 * shows it immediately ({@code SHOW_PUSH_NOTIFICATION}) and the badge count updates
 * ({@code NAV_BADGES}). No-op in a normal browser.
 */
export function postToNativeShell(payload, win = typeof window !== 'undefined' ? window : undefined) {
  const bridge = win && win.ReactNativeWebView
  if (!bridge || typeof bridge.postMessage !== 'function' || !payload) return false
  try {
    bridge.postMessage(
      JSON.stringify({
        type: 'SHOW_PUSH_NOTIFICATION',
        id: String(payload.id || ''),
        title: String(payload.title || ''),
        body: String(payload.body || ''),
        navPath: notificationNavPath(payload)
      })
    )
    if (payload.unread != null) {
      bridge.postMessage(
        JSON.stringify({ type: 'NAV_BADGES', navBadges: { notifications: Number(payload.unread) || 0 } })
      )
    }
    return true
  } catch {
    return false
  }
}

/**
 * Builds a realtime connection. Dependencies are injectable for tests.
 *
 * @param {object} opts
 * @param {() => string} opts.getToken current JWT ('' when signed out)
 * @param {(payload: object) => void} opts.onNotification called for each pushed notification
 * @param {() => void} [opts.onResync] views should refetch (after a reconnect, or fallback tick while down)
 */
export function createNotificationsRealtime({
  getToken,
  onNotification,
  onResync = () => {},
  apiBase = getApiBase,
  pageHref = () => window.location.href,
  ClientImpl = Client,
  postToNative = postToNativeShell,
  setIntervalFn = (fn, ms) => setInterval(fn, ms),
  clearIntervalFn = (id) => clearInterval(id)
}) {
  let client = null
  let token = ''
  let connected = false
  let connects = 0
  let fallbackTimer = null

  function handleFrame(message) {
    let payload
    try {
      payload = JSON.parse(message.body)
    } catch {
      return
    }
    if (!payload || typeof payload !== 'object') return
    try {
      onNotification(payload)
    } finally {
      postToNative(payload)
    }
  }

  function stop() {
    if (fallbackTimer != null) clearIntervalFn(fallbackTimer)
    fallbackTimer = null
    connected = false
    token = ''
    if (client) {
      const c = client
      client = null
      try {
        c.deactivate()
      } catch {
        // ignore
      }
    }
  }

  /** Connects (or keeps the existing connection) for the current token; disconnects when signed out. */
  function sync() {
    const next = String(getToken() || '')
    if (!next) {
      stop()
      return
    }
    if (client && next === token) return
    stop()
    token = next
    connects = 0
    const c = new ClientImpl({
      brokerURL: socketUrl(apiBase(), pageHref(), next),
      reconnectDelay: RECONNECT_INITIAL_MS,
      maxReconnectDelay: RECONNECT_MAX_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      debug: () => {}
    })
    // Re-authenticate every (re)connect with the token that is current at that moment.
    c.beforeConnect = () => {
      const current = String(getToken() || '')
      if (!current) {
        c.deactivate()
        return
      }
      c.brokerURL = socketUrl(apiBase(), pageHref(), current)
    }
    c.onConnect = () => {
      connected = true
      connects += 1
      c.subscribe(NOTIFICATIONS_DESTINATION, handleFrame)
      if (connects > 1) onResync()
    }
    const markDown = () => {
      connected = false
    }
    c.onWebSocketClose = markDown
    c.onStompError = markDown
    c.onWebSocketError = () => {}
    client = c
    fallbackTimer = setIntervalFn(() => {
      if (!connected) onResync()
    }, FALLBACK_POLL_MS)
    c.activate()
  }

  return {
    sync,
    stop,
    isConnected: () => connected
  }
}

// ---------------------------------------------------------------- app singleton

function storedToken() {
  try {
    return localStorage.getItem('ps_token') || ''
  } catch {
    return ''
  }
}

let instance = null

/**
 * Connects once for the signed-in session; safe to call on every auth change (a no-op while the
 * token is unchanged, reconnects when it changes, disconnects when signed out).
 */
export function connectNotificationsRealtime({ onNotification, onResync }) {
  if (!instance) {
    instance = createNotificationsRealtime({
      getToken: storedToken,
      onNotification: (p) => onNotification && onNotification(p),
      onResync: () => onResync && onResync()
    })
  }
  instance.sync()
}

export function disconnectNotificationsRealtime() {
  if (instance) instance.stop()
}

export function isNotificationsRealtimeConnected() {
  return Boolean(instance && instance.isConnected())
}
