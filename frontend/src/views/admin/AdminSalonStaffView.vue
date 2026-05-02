<template>
  <div>
    <template v-if="user">
      <v-card class="admin-card salon-team-hero pa-4 pa-md-5 mb-4" elevation="3" rounded="xl">
        <div class="d-flex flex-column flex-md-row align-start">
          <v-avatar color="primary" size="48" class="mb-3 mb-md-0 mr-md-4">
            <v-icon dark>groups</v-icon>
          </v-avatar>
          <div>
            <div class="text-h6 font-weight-bold mb-1">Staff management</div>
            <p class="text-body-2 text--secondary mb-0">
              Add team members, set who is active, and define <strong>weekly working windows</strong> per person.
              <template v-if="merchantIsSalon">
                For <strong>salon</strong> stores, public booking slots use these windows together with shop opening hours
                (Store) and each service duration.
              </template>
              <template v-else> Use this roster for your team; salon booking uses the same data when you switch to a salon store type. </template>
            </p>
          </div>
        </div>
      </v-card>

      <v-row align="stretch" class="admin-products-row">
        <v-col cols="12" md="5" class="d-flex flex-column">
          <v-card class="admin-card pa-4 pa-sm-6 mb-4 mb-md-0" elevation="3" rounded="xl">
            <div class="card-label mb-6">Add team member</div>
            <v-text-field
              v-model="newStaff.displayName"
              outlined
              hide-details="auto"
              label="Display name"
              hint="Shown when customers pick who performs the service."
              persistent-hint
              class="rounded-lg"
              :disabled="newStaffSubmitting"
            />
            <v-switch
              v-model="newStaff.active"
              label="Active"
              inset
              color="primary"
              class="mt-4"
              hide-details
              :disabled="newStaffSubmitting"
            />
            <v-btn
              block
              x-large
              depressed
              class="mt-6 text-none font-weight-bold btn-admin-primary"
              :loading="newStaffSubmitting"
              @click="submitNewStaff"
            >
              <v-icon left dark>person_add</v-icon>
              Add staff member
            </v-btn>
            <v-alert v-if="newStaffError" type="error" dense outlined class="mt-4 rounded-lg">{{ newStaffError }}</v-alert>
            <v-alert v-if="newStaffSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Staff member saved � set their schedule from the team table.
            </v-alert>
          </v-card>
        </v-col>
        <v-col cols="12" md="7">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="d-flex flex-column flex-sm-row align-start align-sm-center mb-4">
              <div class="card-label mb-0">Team roster</div>
              <v-spacer />
              <v-text-field
                v-model.trim="staffSearch"
                dense
                outlined
                hide-details
                clearable
                label="Search"
                prepend-inner-icon="search"
                class="salon-table-search rounded-lg mt-3 mt-sm-0"
                style="max-width: 280px"
              />
            </div>
            <v-progress-linear v-if="staffLoading" indeterminate height="3" rounded class="mb-2" />
            <v-data-table
              :headers="staffHeaders"
              :items="staffTableFiltered"
              :items-per-page="8"
              class="rounded-lg elevation-0 salon-data-table"
              :loading="staffLoading"
              no-data-text="No staff yet � add someone using the form on the left."
            >
              <template v-slot:[`item.activeLabel`]="{ item }">
                <v-chip small label :color="item.active ? 'success' : 'secondary'" outlined class="text-none">
                  {{ item.activeLabel }}
                </v-chip>
              </template>
              <template v-slot:[`item.windowCount`]="{ item }">
                <span class="font-weight-medium">{{ item.windowCount }}</span>
              </template>
              <template v-slot:[`item.actions`]="{ item }">
                <v-btn
                  small
                  depressed
                  color="primary"
                  class="text-none mr-1 mb-1"
                  @click="openScheduleDialog(item)"
                >
                  <v-icon left x-small>schedule</v-icon>
                  Schedule
                </v-btn>
                <v-btn small outlined color="primary" class="text-none mb-1" @click="openStaffEditDialog(item)">
                  <v-icon left x-small>edit</v-icon>
                  Edit
                </v-btn>
              </template>
            </v-data-table>
          </v-card>
        </v-col>
      </v-row>
    </template>

    <v-dialog
      v-model="staffDialog"
      max-width="480"
      content-class="rounded-xl"
      scrollable
      :persistent="staffSaving"
      @click:outside="closeStaffDialogIfIdle"
    >
      <v-card v-if="staffForm.id" class="rounded-xl">
        <v-card-title class="text-h6 font-weight-bold pb-2">Edit staff</v-card-title>
        <v-card-text class="pt-2">
          <v-text-field
            v-model="staffForm.displayName"
            outlined
            hide-details="auto"
            label="Display name"
            class="rounded-lg"
            :disabled="staffSaving"
          />
          <v-switch v-model="staffForm.active" label="Active" inset color="primary" class="mt-2" hide-details />
          <v-alert v-if="staffError" type="error" dense outlined class="mt-4 rounded-lg mb-0">{{ staffError }}</v-alert>
        </v-card-text>
        <v-card-actions class="px-4 pb-4 pt-0">
          <v-btn text class="text-none" :disabled="staffSaving" @click="closeStaffDialogIfIdle">Cancel</v-btn>
          <v-spacer />
          <v-btn
            depressed
            color="primary"
            class="text-none font-weight-bold btn-amber"
            :loading="staffSaving"
            @click="saveStaffEdit"
          >
            Save staff
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="scheduleDialog"
      max-width="640"
      content-class="rounded-xl"
      scrollable
      :persistent="scheduleBusy"
      @click:outside="closeScheduleDialogIfIdle"
    >
      <v-card v-if="scheduleStaff" class="rounded-xl">
        <v-card-title class="text-h6 font-weight-bold pb-2">
          Weekly schedule � {{ scheduleStaff.displayName }}
        </v-card-title>
        <v-card-text class="pt-2">
          <p class="text-body-2 text--secondary mb-4">
            Each row is one bookable window (ISO weekday 1 = Monday � 7 = Sunday). Slots also respect shop opening hours
            and service length.
          </p>
          <v-progress-linear v-if="scheduleLoading" indeterminate height="3" rounded class="mb-4" />
          <v-data-table
            :headers="scheduleHeaders"
            :items="scheduleRowsForStaff"
            dense
            class="rounded-lg elevation-0 mb-4"
            hide-default-footer
            :items-per-page="-1"
            no-data-text="No windows yet � add one below."
          >
            <template v-slot:[`item.dayLabel`]="{ item }">
              {{ item.dayLabel }}
            </template>
            <template v-slot:[`item.startTime`]="{ item }">
              {{ formatSlotTime(item.startTime) }}
            </template>
            <template v-slot:[`item.endTime`]="{ item }">
              {{ formatSlotTime(item.endTime) }}
            </template>
            <template v-slot:[`item.actions`]="{ item }">
              <v-btn icon small color="error" title="Remove window" :disabled="scheduleBusy" @click="deleteScheduleRow(item)">
                <v-icon small>delete</v-icon>
              </v-btn>
            </template>
          </v-data-table>

          <v-divider class="mb-4" />
          <div class="text-subtitle-2 font-weight-bold mb-2">Add window</div>
          <v-row dense>
            <v-col cols="12" sm="4">
              <v-select
                v-model="addSlotForm.dayOfWeek"
                :items="dayOfWeekItems"
                outlined
                dense
                hide-details="auto"
                label="Weekday"
                item-text="text"
                item-value="value"
                class="rounded-lg"
                :disabled="scheduleBusy"
              />
            </v-col>
            <v-col cols="6" sm="4">
              <v-text-field
                v-model="addSlotForm.startTime"
                type="time"
                outlined
                dense
                hide-details="auto"
                label="From"
                class="rounded-lg"
                :disabled="scheduleBusy"
              />
            </v-col>
            <v-col cols="6" sm="4">
              <v-text-field
                v-model="addSlotForm.endTime"
                type="time"
                outlined
                dense
                hide-details="auto"
                label="To"
                class="rounded-lg"
                :disabled="scheduleBusy"
              />
            </v-col>
          </v-row>
          <v-btn
            depressed
            color="primary"
            class="text-none font-weight-bold mt-2 btn-amber"
            :loading="scheduleSaving"
            :disabled="scheduleBusy"
            @click="submitAddScheduleWindow"
          >
            <v-icon left small color="white">add</v-icon>
            Add window
          </v-btn>
          <v-alert v-if="scheduleError" type="error" dense outlined class="mt-4 rounded-lg mb-0">{{ scheduleError }}</v-alert>
        </v-card-text>
        <v-card-actions class="px-4 pb-4 pt-0">
          <v-spacer />
          <v-btn text class="text-none" :disabled="scheduleBusy" @click="closeScheduleDialogIfIdle">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import { fetchAdminStoreSettings } from '@/services/adminApi'
import { isSalonShopType } from '@/services/shopType'
import {
  fetchAdminSalonStaff,
  fetchAdminSalonAvailability,
  deleteAdminSalonAvailability,
  addAdminSalonAvailability,
  upsertAdminSalonStaff
} from '@/services/salonAdmin'

export default {
  name: 'AdminSalonStaffView',
  inject: {
    adminSession: { default: null }
  },
  data() {
    return {
      merchantIsSalon: false,
      staff: [],
      staffLoading: false,
      newStaff: { displayName: '', active: true },
      newStaffSubmitting: false,
      newStaffError: '',
      newStaffSuccess: false,
      staffDialog: false,
      staffSaving: false,
      staffError: '',
      staffForm: { id: null, displayName: '', active: true },
      staffSearch: '',
      availabilityList: [],
      scheduleDialog: false,
      scheduleStaff: null,
      scheduleLoading: false,
      scheduleSaving: false,
      scheduleError: '',
      addSlotForm: { dayOfWeek: 1, startTime: '09:00', endTime: '17:00' }
    }
  },
  computed: {
    user() {
      return this.adminSession && this.adminSession.user ? this.adminSession.user : null
    },
    staffHeaders() {
      return [
        { text: 'Name', value: 'displayName', sortable: true },
        { text: 'Status', value: 'activeLabel', sortable: true },
        { text: 'Bookable windows', value: 'windowCount', align: 'end', sortable: true },
        { text: '', value: 'actions', sortable: false, align: 'end', width: '220' }
      ]
    },
    dayOfWeekItems() {
      return [
        { text: 'Monday', value: 1 },
        { text: 'Tuesday', value: 2 },
        { text: 'Wednesday', value: 3 },
        { text: 'Thursday', value: 4 },
        { text: 'Friday', value: 5 },
        { text: 'Saturday', value: 6 },
        { text: 'Sunday', value: 7 }
      ]
    },
    staffTableItems() {
      const counts = {}
      ;(this.availabilityList || []).forEach((a) => {
        const sid = a && a.staffId != null ? String(a.staffId) : ''
        if (!sid) return
        counts[sid] = (counts[sid] || 0) + 1
      })
      return (this.staff || []).map((m) => ({
        ...m,
        activeLabel: m.active !== false ? 'Active' : 'Inactive',
        windowCount: counts[String(m.id)] || 0
      }))
    },
    staffTableFiltered() {
      const q = String(this.staffSearch || '').trim().toLowerCase()
      if (!q) return this.staffTableItems
      return this.staffTableItems.filter((r) => String(r.displayName || '').toLowerCase().includes(q))
    },
    scheduleBusy() {
      return this.scheduleLoading || this.scheduleSaving
    },
    scheduleHeaders() {
      return [
        { text: 'Day', value: 'dayLabel', sortable: false },
        { text: 'From', value: 'startTime', sortable: false },
        { text: 'To', value: 'endTime', sortable: false },
        { text: '', value: 'actions', sortable: false, align: 'end', width: '56' }
      ]
    },
    scheduleRowsForStaff() {
      if (!this.scheduleStaff || !this.scheduleStaff.id) return []
      const sid = String(this.scheduleStaff.id)
      return (this.availabilityList || [])
        .filter((a) => a && String(a.staffId) === sid)
        .map((a) => ({
          ...a,
          dayLabel: this.dayLabelFor(a.dayOfWeek)
        }))
    }
  },
  watch: {
    user(u) {
      if (u) this.bootstrap()
    },
    '$route.params.merchantSlug'() {
      if (this.user) this.bootstrap()
    }
  },
  created() {
    if (this.user) this.bootstrap()
  },
  methods: {
    async bootstrap() {
      try {
        const s = await fetchAdminStoreSettings(this.$route)
        this.merchantIsSalon = isSalonShopType(s.shopType)
      } catch {
        this.merchantIsSalon = false
      }
      await Promise.all([this.loadStaff(), this.loadAvailability()])
    },
    dayLabelFor(dow) {
      const n = Number(dow)
      const hit = this.dayOfWeekItems.find((x) => x.value === n)
      return hit ? hit.text : String(dow)
    },
    formatSlotTime(t) {
      if (t == null) return '\u2014'
      const s = String(t).trim()
      if (s.length >= 5) return s.slice(0, 5)
      return s
    },
    async loadAvailability() {
      try {
        const rows = await fetchAdminSalonAvailability(this.$route)
        this.availabilityList = Array.isArray(rows) ? rows : []
      } catch {
        this.availabilityList = []
      }
    },
    async loadStaff() {
      this.staffLoading = true
      try {
        this.staff = await fetchAdminSalonStaff(this.$route, true)
      } catch {
        this.staff = []
      } finally {
        this.staffLoading = false
      }
    },
    async submitNewStaff() {
      this.newStaffError = ''
      this.newStaffSuccess = false
      const displayName = String(this.newStaff.displayName || '').trim()
      if (!displayName) {
        this.newStaffError = 'Display name is required.'
        return
      }
      this.newStaffSubmitting = true
      try {
        await upsertAdminSalonStaff(this.$route, {
          id: null,
          displayName,
          active: this.newStaff.active
        })
        this.newStaff = { displayName: '', active: true }
        this.newStaffSuccess = true
        setTimeout(() => {
          this.newStaffSuccess = false
        }, 3500)
        await this.loadStaff()
        await this.loadAvailability()
      } catch (e) {
        this.newStaffError = e && e.message ? e.message : 'Could not add staff.'
      } finally {
        this.newStaffSubmitting = false
      }
    },
    openStaffEditDialog(row) {
      if (!row || !row.id) return
      this.staffError = ''
      this.staffForm = {
        id: row.id,
        displayName: row.displayName || '',
        active: row.active !== false
      }
      this.staffDialog = true
    },
    closeStaffDialogIfIdle() {
      if (this.staffSaving) return
      this.staffDialog = false
    },
    async saveStaffEdit() {
      this.staffError = ''
      this.staffSaving = true
      try {
        const id = this.staffForm.id ? String(this.staffForm.id) : null
        await upsertAdminSalonStaff(this.$route, {
          id,
          displayName: this.staffForm.displayName,
          active: this.staffForm.active
        })
        this.staffDialog = false
        await this.loadStaff()
        await this.loadAvailability()
      } catch (e) {
        this.staffError = e && e.message ? e.message : 'Could not save staff.'
      } finally {
        this.staffSaving = false
      }
    },
    openScheduleDialog(member) {
      if (!member || !member.id) return
      this.scheduleError = ''
      this.scheduleStaff = { ...member }
      this.addSlotForm = { dayOfWeek: 1, startTime: '09:00', endTime: '17:00' }
      this.scheduleDialog = true
      this.scheduleLoading = true
      this.loadAvailability()
        .catch(() => {})
        .finally(() => {
          this.scheduleLoading = false
        })
    },
    closeScheduleDialogIfIdle() {
      if (this.scheduleBusy) return
      this.scheduleDialog = false
      this.scheduleStaff = null
    },
    async deleteScheduleRow(row) {
      if (!row || !row.id) return
      this.scheduleError = ''
      this.scheduleLoading = true
      try {
        await deleteAdminSalonAvailability(this.$route, row.id)
        await this.loadAvailability()
      } catch (e) {
        this.scheduleError = e && e.message ? e.message : 'Could not remove window.'
      } finally {
        this.scheduleLoading = false
      }
    },
    async submitAddScheduleWindow() {
      if (!this.scheduleStaff || !this.scheduleStaff.id) return
      this.scheduleError = ''
      this.scheduleSaving = true
      try {
        await addAdminSalonAvailability(this.$route, {
          staffId: this.scheduleStaff.id,
          dayOfWeek: Number(this.addSlotForm.dayOfWeek),
          startTime: String(this.addSlotForm.startTime || '').trim(),
          endTime: String(this.addSlotForm.endTime || '').trim()
        })
        await this.loadAvailability()
      } catch (e) {
        this.scheduleError = e && e.message ? e.message : 'Could not add window.'
      } finally {
        this.scheduleSaving = false
      }
    }
  }
}
</script>

<style scoped>
.salon-team-hero {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.98) 0%, rgba(241, 245, 249, 0.95) 100%);
}
.salon-data-table ::v-deep th {
  font-weight: 700 !important;
  font-size: 12px !important;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: rgba(15, 23, 42, 0.55) !important;
}
</style>
