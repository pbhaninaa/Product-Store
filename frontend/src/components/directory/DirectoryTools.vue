<template>
  <div class="dir-tools">
    <div class="dir-tools__locate">
      <v-btn
        v-if="!hasLocation"
        text
        color="primary"
        class="text-none font-weight-medium px-2"
        :loading="locating"
        @click="$emit('locate')"
      >
        <v-icon left small>my_location</v-icon>
        Nearest first
      </v-btn>
      <v-chip v-else close class="text-none" @click:close="$emit('clear-location')">
        <v-icon left small>place</v-icon>
        Nearest first
      </v-chip>
    </div>
    <v-text-field
      :value="value"
      outlined
      clearable
      hide-details="auto"
      dense
      label="Search by provider name, item or service"
      aria-label="Search providers"
      :rules="[rules.maxLength(100, 'Search')]"
      prepend-inner-icon="search"
      class="dir-tools__search"
      @input="$emit('input', $event || '')"
    />
  </div>
</template>

<script>
import { rules } from '@/utils/validation'

/** "Nearest first" + provider search for the directory; placed inside or below the pinned chips. */
export default {
  name: 'DirectoryTools',
  props: {
    value: { type: String, default: '' },
    hasLocation: { type: Boolean, default: false },
    locating: { type: Boolean, default: false }
  },
  data() {
    return { rules }
  }
}
</script>

<style scoped>
/* Phone: stacked. Larger screens: "Nearest first" beside a full-width search box. */
.dir-tools {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@media (min-width: 600px) {
  .dir-tools {
    flex-direction: row;
    align-items: center;
  }

  .dir-tools__locate {
    order: 2;
    flex-shrink: 0;
  }

  .dir-tools__search {
    flex: 1 1 auto;
  }
}
</style>
