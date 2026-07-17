function merchantAdminRoute(route, name, extraQuery) {
  const slug = String(route && route.params && route.params.merchantSlug ? route.params.merchantSlug : '').trim()
  return slug
    ? { name, params: { merchantSlug: slug }, query: extraQuery || {} }
    : null
}

export function resolveNotificationLink(notification, context = {}) {
  if (!notification) return null
  const type = String(notification.notificationType || '').trim().toUpperCase()
  const refType = String(notification.referenceType || '').trim().toUpperCase()
  const refId = String(notification.referenceId || '').trim()
  const isSupport = Boolean(context.isSupport)
  const route = context.route || null

  if (isSupport) {
    if (type === 'SUBSCRIPTION_PROOF_PENDING') {
      return {
        to: { name: 'support-subscriptions', query: refId ? { tab: 'proofs', tenantId: refId } : { tab: 'proofs' } },
        label: 'Review proof',
      }
    }
    if (type === 'SUPPORT_TICKET') {
      return {
        to: { name: 'support-tickets', query: refId ? { ticketId: refId } : {} },
        label: 'Open ticket',
      }
    }
    return null
  }

  if (refType === 'ORDER') {
    return {
      to: merchantAdminRoute(route, 'merchant-admin-orders', refId ? { orderId: refId } : {}),
      label: type === 'ORDER_EFT_REVIEW' ? 'Review payment' : 'View order',
    }
  }

  if (refType === 'SALON_BOOKING') {
    return {
      to: merchantAdminRoute(route, 'merchant-admin-salon-bookings', refId ? { bookingId: refId } : {}),
      label: type === 'BOOKING_EFT_REVIEW' ? 'Review booking payment' : 'View booking',
    }
  }

  if (refType === 'SUBSCRIPTION') {
    return {
      to: merchantAdminRoute(route, 'merchant-admin-subscription'),
      label: type === 'SUBSCRIPTION_ACTIVATED' ? 'View billing' : 'Plan & billing',
    }
  }

  if (refType === 'TICKET') {
    return {
      to: merchantAdminRoute(route, 'merchant-admin-help', refId ? { ticketId: refId } : {}),
      label: 'View support ticket',
    }
  }

  return null
}
