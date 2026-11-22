import { effectiveAccess, hasPermission } from '@/utils/merchantPermissions'

/**
 * What the signed-in member may do in this store, from the AdminLayout's shared `adminSession`
 * (`adminSession.access` is the `/admin/access/me` response). UX only: the server enforces every permission.
 */
export default {
  inject: {
    adminSession: {
      default: null
    }
  },
  computed: {
    adminAccess() {
      const s = this.adminSession
      return effectiveAccess(s && s.user, s && s.access)
    },
    isStoreOwner() {
      return this.adminAccess.isOwner
    }
  },
  methods: {
    /** True when the signed-in member holds the permission (owners hold all). */
    can(permission) {
      return hasPermission(this.adminAccess, permission)
    }
  }
}
