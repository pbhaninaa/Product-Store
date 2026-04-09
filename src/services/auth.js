import { supabase, supabaseReady } from '@/supabase'

export function subscribeToAuth(callback) {
  if (!supabaseReady || !supabase) {
    callback(null)
    return () => {}
  }

  supabase.auth.getSession().then(({ data: { session } }) => {
    callback(session?.user ?? null)
  })

  const {
    data: { subscription }
  } = supabase.auth.onAuthStateChange((_event, session) => {
    callback(session?.user ?? null)
  })

  return () => {
    subscription.unsubscribe()
  }
}

export async function loginWithEmailPassword(email, password) {
  if (!supabaseReady || !supabase) {
    throw new Error('Supabase is not configured. Set .env and restart npm run serve.')
  }
  const e = String(email || '').trim()
  const p = String(password || '')
  if (!e || !p) throw new Error('Email and password are required.')

  const { error } = await supabase.auth.signInWithPassword({ email: e, password: p })
  if (error) throw error
}

export async function logout() {
  if (!supabaseReady || !supabase) return
  await supabase.auth.signOut()
}
