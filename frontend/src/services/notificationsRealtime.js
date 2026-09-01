import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getApiBase } from '@/services/api'

function isLoopbackHost(hostname) {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]'
}

function wsHttpBase() {
  const base = (getApiBase() || '').replace(/\/api\/?$/i, '').trim()
  if (typeof window === 'undefined') {
    return base || ''
  }
  const page = new URL(window.location.href)
  if (!base) {
    return `${page.protocol}//${page.host}`
  }
  try {
    const api = new URL(base.startsWith('http') ? base : `${page.protocol}//${base}`)
    if (api.origin === page.origin) {
      return `${page.protocol}//${page.host}`
    }
    const securePage = page.protocol === 'https:'
    const insecureApi = api.protocol === 'http:'
    if (securePage && insecureApi && isLoopbackHost(api.hostname)) {
      return `${page.protocol}//${page.host}`
    }
    return `${api.protocol}//${api.host}`
  } catch {
    return base || `${page.protocol}//${page.host}`
  }
}

function secureSockJsHttpUrl(url) {
  if (typeof window === 'undefined' || window.location.protocol !== 'https:') {
    return url
  }
  try {
    const u = new URL(url, window.location.origin)
    if (u.protocol === 'http:' && isLoopbackHost(u.hostname)) {
      return url
    }
    if (u.protocol === 'http:') {
      u.protocol = 'https:'
    }
    return u.toString()
  } catch {
    return url.replace(/^http:\/\//i, 'https://')
  }
}

let client = null

export function disconnectNotificationsRealtime() {
  if (client) {
    try {
      client.deactivate()
    } catch {
      // ignore
    }
    client = null
  }
}

/** STOMP over SockJS to /user/queue/notifications with JWT in the query string. */
export function connectNotificationsRealtime(onPayload) {
  disconnectNotificationsRealtime()
  let token = ''
  try {
    token = localStorage.getItem('ps_token') || ''
  } catch {
    token = ''
  }
  if (!token) return

  const url = secureSockJsHttpUrl(`${wsHttpBase()}/ws?access_token=${encodeURIComponent(token)}`)
  client = new Client({
    reconnectDelay: 4000,
    heartbeatIncoming: 15000,
    heartbeatOutgoing: 15000,
    webSocketFactory: () => new SockJS(url),
    debug: () => {},
    onStompError: () => {},
    onWebSocketError: () => {},
    onConnect: () => {
      client.subscribe('/user/queue/notifications', (message) => {
        try {
          const payload = JSON.parse(message.body)
          onPayload && onPayload(payload)
        } catch {
          // ignore
        }
      })
    }
  })
  client.activate()
}
