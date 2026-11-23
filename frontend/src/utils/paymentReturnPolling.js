/**
 * Shared polling for the online payment return page.
 * The host component provides `checkPaid()` → Promise<boolean>; it should throw on a hard error.
 *
 * Polls never overlap: the next check is scheduled POLL_INTERVAL_MS after the previous one finished, so a slow
 * status endpoint (which may call Paystack) gets at most one request at a time and fewer than 20 a minute.
 * After MAX_ATTEMPTS checks (about 45 seconds) without confirmation, `timedOut` is set so the page can offer
 * a "Check again" button instead of spinning forever.
 */
export const POLL_INTERVAL_MS = 3000
export const MAX_ATTEMPTS = 15

export const paymentReturnPolling = {
  data() {
    return {
      polling: false,
      timedOut: false,
      error: '',
      paid: false,
      attempts: 0,
      pollTimer: null,
      /** Bumped on every start so a check still in flight from an earlier run cannot schedule another. */
      pollRun: 0
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    stopPolling() {
      if (this.pollTimer) {
        clearTimeout(this.pollTimer)
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
    /** One check, then (if still polling) the next one after the interval. */
    async pollLoop(run) {
      await this.pollOnce()
      if (run !== this.pollRun || !this.polling) return
      this.attempts += 1
      if (this.attempts >= MAX_ATTEMPTS) {
        this.stopPolling()
        if (!this.paid && !this.error) this.timedOut = true
        return
      }
      this.pollTimer = setTimeout(() => {
        this.pollTimer = null
        void this.pollLoop(run)
      }, POLL_INTERVAL_MS)
    },
    startPolling() {
      this.stopPolling()
      this.error = ''
      this.timedOut = false
      this.polling = true
      this.attempts = 0
      this.pollRun += 1
      void this.pollLoop(this.pollRun)
    }
  }
}
