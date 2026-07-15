import { apiFetch } from '@/services/api'
import { requireMerchantSlugForApi } from '@/services/auth'

function slugFromRoute(route) {
  return requireMerchantSlugForApi(route)
}

export async function fetchTeam(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/team`, { auth: true })
}

export async function createTeamMember(route, body) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/team`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

export async function updateTeamMember(route, employeeId, body) {
  const slug = slugFromRoute(route)
  return await apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/team/${encodeURIComponent(employeeId)}`,
    { method: 'PUT', json: body, auth: true }
  )
}

export async function deactivateTeamMember(route, employeeId) {
  const slug = slugFromRoute(route)
  return await apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/team/${encodeURIComponent(employeeId)}`,
    { method: 'DELETE', auth: true }
  )
}

export async function fetchPaymentCalculations(route, { startDate, endDate } = {}) {
  const slug = slugFromRoute(route)
  const q = new URLSearchParams()
  if (startDate) q.set('startDate', startDate)
  if (endDate) q.set('endDate', endDate)
  const qs = q.toString()
  return await apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/team/payment-calculations${qs ? `?${qs}` : ''}`,
    { auth: true }
  )
}

export async function fetchMyExpectedIncome(route, { startDate, endDate } = {}) {
  const slug = slugFromRoute(route)
  const q = new URLSearchParams()
  if (startDate) q.set('startDate', startDate)
  if (endDate) q.set('endDate', endDate)
  const qs = q.toString()
  return await apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/team/my-expected-income${qs ? `?${qs}` : ''}`,
    { auth: true }
  )
}

export async function markPayrollJobPaid(route, body) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/team/payroll-marks`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

export async function unmarkPayrollJobPaid(route, body) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/team/payroll-marks`, {
    method: 'DELETE',
    json: body,
    auth: true
  })
}

export async function payAllPayroll(route, body) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/team/payroll-marks/pay-all`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

export async function assignJobToEmployee(route, body) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/team/assign-job`, {
    method: 'POST',
    json: body,
    auth: true
  })
}

export async function fetchNotifications(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/notifications`, { auth: true })
}

export async function fetchNotificationUnreadCount(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/notifications/unread-count`, {
    auth: true
  })
}

export async function markNotificationRead(route, notificationId) {
  const slug = slugFromRoute(route)
  return await apiFetch(
    `/api/m/${encodeURIComponent(slug)}/admin/notifications/${encodeURIComponent(notificationId)}/read`,
    { method: 'POST', auth: true }
  )
}

export async function markAllNotificationsRead(route) {
  const slug = slugFromRoute(route)
  return await apiFetch(`/api/m/${encodeURIComponent(slug)}/admin/notifications/read-all`, {
    method: 'POST',
    auth: true
  })
}
