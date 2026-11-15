/**
 * Shared polling for the hosted-payment return pages (PayFast and legacy Peach).
 * The host component provides `checkPaid()` → Promise<boolean>; it should throw on a hard error.
 * After MAX_ATTEMPTS polls (~40s) without confirmation, `timedOut` is set so the page can offer
 * a "Check again" button instead of spinning forever.
 */
export const POLL_INTERVAL_MS = 2000
export const MAX_ATTEMPTS = 20

export const paymentReturnPolling = {
  data() {
    return {
      polling: false,
      timedOut: false,
      error: '',
      paid: false,
      attempts: 0,
      pollTimer: null
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      this.polling = false
    },
    async pollOnce() {
      try {
        const ok = await this.checkPaid()
        if (ok) {
          this.paid = true
          this.timedOut = false
          this.stopPolling()
        }
      } catch (e) {
        this.error = e && e.message ? e.message : 'Could not verify payment status.'
        this.stopPolling()
      }
    },
    startPolling() {
      this.stopPolling()
      this.error = ''
      this.timedOut = false
      this.polling = true
      this.attempts = 0
      void this.pollOnce()
      this.pollTimer = setInterval(() => {
        this.attempts += 1
        if (this.attempts >= MAX_ATTEMPTS) {
          this.stopPolling()
          if (!this.paid && !this.error) this.timedOut = true
          return
        }
        void this.pollOnce()
      }, POLL_INTERVAL_MS)
    }
  }
}
