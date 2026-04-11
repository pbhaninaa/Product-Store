<template>
  <div>
    <v-row v-if="user" class="mt-0 mt-sm-2">
      <v-col cols="12">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Sales & performance</div>
            </div>
            <p class="text-caption text--secondary mb-4">
              Filter by period and order type. Revenue uses each order’s total (including delivery).
              <strong>Top selling product</strong> is the one with the <strong>most units sold</strong> in this period (e.g. 5
              juice vs 2 blocks → juice). The table lists all products by units, then line revenue.
              <template v-if="statsOnlyPaid">Only <strong>confirmed payments</strong> are included.</template>
              <template v-else>Unpaid orders are included — totals may change when payments are confirmed.</template>
              Cancelled orders are always excluded.
            </p>

            <div class="stats-toolbar mb-4">
              <v-select
                v-model="statsPreset"
                :items="statsPresetItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                label="Period"
                class="stats-field stats-preset rounded-lg"
                @change="onStatsPresetChange"
              />
              <v-text-field
                v-model="statsDateFrom"
                outlined
                dense
                hide-details
                label="From"
                type="date"
                class="stats-field stats-date rounded-lg"
                @input="bumpStatsToCustom"
              />
              <v-text-field
                v-model="statsDateTo"
                outlined
                dense
                hide-details
                label="To"
                type="date"
                class="stats-field stats-date rounded-lg"
                @input="bumpStatsToCustom"
              />
              <v-select
                v-model="statsDelType"
                :items="statsDelTypeItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Delivery"
                class="stats-field stats-filter rounded-lg"
              />
              <v-select
                v-model="statsPayMethod"
                :items="statsPayMethodItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Payment"
                class="stats-field stats-filter rounded-lg"
              />
              <v-select
                v-model="statsCategoryFilter"
                :items="statsCategoryFilterItems"
                item-text="text"
                item-value="value"
                outlined
                dense
                hide-details
                clearable
                label="Product category"
                class="stats-field stats-filter rounded-lg"
              />
              <v-switch
                v-model="statsOnlyPaid"
                hide-details
                class="stats-switch mt-sm-2"
                label="Confirmed payments only"
              />
            </div>

            <div v-if="ordersLoading" class="py-6 text-center">
              <v-progress-circular indeterminate color="primary" size="28" width="2" />
            </div>

            <template v-else>
              <div v-if="!statsFilteredOrders.length" class="muted-panel rounded-lg pa-8 text-center">
                <v-icon size="40" color="secondary" class="mb-3">insert_chart_outlined</v-icon>
                <div class="text-subtitle-1 font-weight-bold mb-1">No data for these filters</div>
                <div class="text-body-2 text--secondary">
                  Widen the date range, turn off “confirmed only”, or clear filters — or wait for matching orders.
                </div>
              </div>

              <template v-else>
                <v-row dense class="mb-6">
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile stat-tile--accent rounded-xl pa-4">
                      <div class="stat-tile__label">Revenue</div>
                      <div class="stat-tile__value">{{ formatZar(statsTotalRevenue) }}</div>
                      <div class="stat-tile__hint">{{ statsFilteredOrders.length }} orders</div>
                    </div>
                  </v-col>
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile rounded-xl pa-4">
                      <div class="stat-tile__label">Subtotal (items)</div>
                      <div class="stat-tile__value">{{ formatZar(statsTotalSubtotal) }}</div>
                      <div class="stat-tile__hint">Before delivery</div>
                    </div>
                  </v-col>
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile rounded-xl pa-4">
                      <div class="stat-tile__label">Delivery fees</div>
                      <div class="stat-tile__value">{{ formatZar(statsTotalDelivery) }}</div>
                      <div class="stat-tile__hint">Collected on orders</div>
                    </div>
                  </v-col>
                  <v-col cols="12" sm="6" md="3">
                    <div class="stat-tile rounded-xl pa-4">
                      <div class="stat-tile__label">Avg. order value</div>
                      <div class="stat-tile__value">{{ formatZar(statsAvgOrderValue) }}</div>
                      <div class="stat-tile__hint">Per order in range</div>
                    </div>
                  </v-col>
                </v-row>

                <v-alert
                  v-if="statsTopProductsCategoryFallback"
                  dense
                  text
                  type="info"
                  border="left"
                  colored-border
                  class="mb-4 rounded-lg"
                >
                  No sales in <strong>{{ statsCategoryFilter }}</strong> for this period — showing top sellers across
                  <strong>all</strong> categories.
                </v-alert>

                <div
                  v-if="statsTopSellingByUnits"
                  class="stat-tile stat-tile--accent stat-tile--top-seller rounded-xl pa-6 mb-6"
                >
                  <div class="stat-tile__label">Top selling product</div>
                  <div class="stat-tile--top-seller-name">{{ statsTopSellingByUnits.name }}</div>
                  <div class="stat-tile__hint mt-2 mb-0">
                    {{ statsTopSellingByUnits.units }} unit{{ statsTopSellingByUnits.units === 1 ? '' : 's' }} sold in this
                    period (most units in your selection).
                  </div>
                </div>

                <div v-if="false" class="text-subtitle-2 font-weight-bold mb-2">All products by units sold</div>
                <v-simple-table v-if="false"  dense class="stats-table rounded-lg elevation-1">
                  <template #default>
                    <thead>
                      <tr>
                        <th class="text-left">#</th>
                        <th class="text-left">Product</th>
                        <th class="text-right">Units</th>
                        <th class="text-right">Line total</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, idx) in statsProductsByUnits" :key="'u-' + row.productId">
                        <td>{{ idx + 1 }}</td>
                        <td>{{ row.name }}</td>
                        <td class="text-right">{{ row.units }}</td>
                        <td class="text-right">{{ formatZar(row.revenue) }}</td>
                      </tr>
                    </tbody>
                  </template>
                </v-simple-table>
               
              </template>
            </template>
          </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminInsightsView',
  mixins: [adminModuleMixin]
}
</script>
