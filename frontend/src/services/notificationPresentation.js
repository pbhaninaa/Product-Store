const DEFAULT_PRESENTATION = {
  title: 'Notification',
  icon: 'notifications_none',
  color: 'primary',
  category: 'Update',
  tone: 'info',
}

const TYPE_META = {
  ORDER_PLACED: {
    title: 'New order',
    icon: 'receipt_long',
    color: 'primary',
    category: 'Orders',
    tone: 'new',
  },
  ORDER_EFT_REVIEW: {
    title: 'EFT review needed',
    icon: 'account_balance',
    color: 'warning',
    category: 'Orders',
    tone: 'warning',
  },
  BOOKING_PLACED: {
    title: 'New booking',
    icon: 'event_note',
    color: 'primary',
    category: 'Bookings',
    tone: 'new',
  },
  BOOKING_EFT_REVIEW: {
    title: 'Booking EFT review',
    icon: 'account_balance_wallet',
    color: 'warning',
    category: 'Bookings',
    tone: 'warning',
  },
  SUBSCRIPTION_ACTION_REQUIRED: {
    title: 'Subscription action required',
    icon: 'credit_card',
    color: 'warning',
    category: 'Billing',
    tone: 'action',
  },
  SUBSCRIPTION_ACTIVATED: {
    title: 'Subscription active',
    icon: 'verified',
    color: 'success',
    category: 'Billing',
    tone: 'success',
  },
  SUBSCRIPTION_PROOF_REJECTED: {
    title: 'Proof rejected',
    icon: 'cancel',
    color: 'error',
    category: 'Billing',
    tone: 'error',
  },
  SUBSCRIPTION_PROOF_PENDING: {
    title: 'Proof review',
    icon: 'description',
    color: 'deep-purple',
    category: 'Billing',
    tone: 'action',
  },
  SUPPORT_TICKET: {
    title: 'New help ticket',
    icon: 'support_agent',
    color: 'deep-orange',
    category: 'Support',
    tone: 'action',
  },
  SUPPORT_TICKET_RESOLVED: {
    title: 'Help ticket resolved',
    icon: 'task_alt',
    color: 'success',
    category: 'Support',
    tone: 'success',
  },
}

export function getNotificationPresentation(notification) {
  const type = String(notification && notification.notificationType ? notification.notificationType : '').trim().toUpperCase()
  return { ...DEFAULT_PRESENTATION, ...(TYPE_META[type] || {}) }
}

export function truncateNotificationText(text, max = 140) {
  const value = String(text || '').trim()
  if (!value) return ''
  if (value.length <= max) return value
  return `${value.slice(0, max - 1).trim()}...`
}
