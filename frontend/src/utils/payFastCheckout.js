/** Auto-submit a signed PayFast hosted-checkout form. */
export function submitPayFastForm(processUrl, fields) {
  if (!processUrl || !fields || typeof fields !== 'object') {
    throw new Error('PayFast checkout is missing form fields')
  }
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = String(processUrl)
  form.style.display = 'none'
  for (const [key, value] of Object.entries(fields)) {
    if (key == null || value == null) continue
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = String(key)
    input.value = String(value)
    form.appendChild(input)
  }
  document.body.appendChild(form)
  form.submit()
}

export function startHostedCheckout(session) {
  if (session && session.processUrl && session.fields) {
    submitPayFastForm(session.processUrl, session.fields)
    return true
  }
  if (session && session.peachRedirectUrl) {
    window.location.href = session.peachRedirectUrl
    return true
  }
  return false
}
