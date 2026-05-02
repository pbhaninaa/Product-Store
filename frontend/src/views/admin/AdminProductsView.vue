<template>
  <div>
    <v-row v-if="user" align="stretch" class="admin-products-row">
      <v-col cols="12" md="5" class="d-flex flex-column">
          <v-card class="admin-card pa-4 pa-sm-6 mb-4 mb-md-0" elevation="3" rounded="xl">
            <div class="card-label mb-6">New product</div>

            <v-text-field
              v-model="name"
              outlined
              hide-details="auto"
              label="Product name"
              class="rounded-lg"
              :disabled="submitting"
            />

            <v-text-field
              v-model="category"
              outlined
              hide-details="auto"
              label="Category"
              hint="Buyers use this to filter the shop (e.g. Clothing, Home, Accessories)."
              persistent-hint
              class="mt-2 rounded-lg"
              :disabled="submitting"
            />

            <v-text-field
              v-model="price"
              outlined
              hide-details="auto"
              label="Price (e.g. 100)"
              type="number"
              class="mt-2 rounded-lg"
              :disabled="submitting"
            />

            <v-text-field
              v-model="initialStock"
              outlined
              hide-details="auto"
              label="Stock quantity"
              type="number"
              min="0"
              hint="How many units you have ready to sell. Customers cannot order more than this."
              persistent-hint
              class="mt-2 rounded-lg"
              :disabled="submitting"
            />

            <v-file-input
              v-model="file"
              outlined
              hide-details="auto"
              accept="image/*"
              label="Product image"
              class="mt-2 rounded-lg"
              :disabled="submitting"
              prepend-icon="image"
              show-size
            />

            <v-btn
              block
              x-large
              depressed
              class="mt-6 text-none font-weight-bold btn-admin-primary"
              :loading="submitting"
              @click="submit"
            >
              <v-icon left dark>cloud_upload</v-icon>
              Publish product
            </v-btn>

            <v-alert v-if="submitError" type="error" dense outlined class="mt-4 rounded-lg">
              {{ submitError }}
            </v-alert>
            <v-alert v-if="submitSuccess" type="success" dense outlined class="mt-4 rounded-lg">
              Product uploaded — it should appear on the shop immediately.
            </v-alert>
          </v-card>
      </v-col>
      <v-col cols="12" md="7">
          <v-card class="admin-card pa-4 pa-sm-6" elevation="3" rounded="xl">
            <div class="d-flex align-center flex-wrap mb-2">
              <div class="card-label mb-0">Inventory</div>
              <v-spacer />
              <div
                v-if="!loading && sortedProducts.length"
                class="text-caption text--secondary mr-2"
              >
                <template v-if="filteredInventoryProducts.length">
                  Items {{ inventoryRangeFrom }}–{{ inventoryRangeTo }} of {{ filteredInventoryProducts.length }}
                  <span
                    v-if="filteredInventoryProducts.length !== sortedProducts.length"
                    class="text--disabled"
                  > ({{ sortedProducts.length }} in catalog)</span>
                </template>
              </div>
              <v-progress-circular v-if="loading" indeterminate size="22" width="2" color="accent" />
            </div>
            <p class="text-caption text--secondary mb-4">
              Edit <strong>category</strong> and <strong>quantity</strong>, then click <strong>Save changes</strong>.
              Sales reduce stock automatically when orders are placed. Use <strong>Delete</strong> to remove a product
              and its image.
            </p>

            <v-alert v-if="inventoryError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ inventoryError }}
            </v-alert>

            <v-alert v-if="deleteError" type="error" dense outlined class="mb-4 rounded-lg">
              {{ deleteError }}
            </v-alert>

            <div v-if="!loading && sortedProducts.length === 0" class="muted-panel rounded-lg pa-6 pa-sm-8 text-center">
              <v-icon size="40" color="secondary" class="mb-3">inventory</v-icon>
              <div class="text-subtitle-1 font-weight-bold mb-1">No products yet</div>
              <div class="text-body-2 text--secondary d-none d-md-block">Add one using the form on the left.</div>
              <div class="text-body-2 text--secondary d-md-none">Add one using the new product form above.</div>
            </div>

            <template v-else>
              <div class="inventory-toolbar mb-4">
                <v-text-field
                  v-model="inventorySearch"
                  outlined
                  dense
                  hide-details
                  clearable
                  prepend-inner-icon="search"
                  label="Search products"
                  placeholder="Name or category…"
                  class="inventory-search-field rounded-lg"
                  aria-label="Search inventory"
                />
                <v-select
                  v-model="inventoryCategoryFilter"
                  :items="inventoryCategoryMenuItems"
                  item-text="text"
                  item-value="value"
                  outlined
                  dense
                  hide-details
                  clearable
                  label="Category"
                  prepend-inner-icon="category"
                  class="inventory-filter-select rounded-lg"
                  aria-label="Filter by category"
                />
              </div>

              <div
                v-if="!filteredInventoryProducts.length"
                class="muted-panel rounded-lg pa-6 text-center"
              >
                <v-icon size="36" color="secondary" class="mb-2">manage_search</v-icon>
                <div class="text-subtitle-2 font-weight-bold mb-1">No matching products</div>
                <div class="text-body-2 text--secondary mb-0">
                  Try a different search or choose <strong>All categories</strong>.
                </div>
              </div>

              <div v-else class="admin-inventory-wrap">
                <div class="admin-inventory-list">
                  <div
                    v-for="p in paginatedInventoryProducts"
                    :key="p.id"
                    class="admin-inventory-card"
                  >
                <div class="admin-inventory-card__details">
                  <div class="admin-inventory-card__thumb rounded-lg">
                    <v-img :src="p.imageUrl" height="72" max-width="72" class="rounded-lg">
                      <template #placeholder>
                        <v-row class="fill-height ma-0" align="center" justify="center">
                          <v-progress-circular indeterminate size="20" width="2" />
                        </v-row>
                      </template>
                    </v-img>
                  </div>
                  <div class="admin-inventory-card__text">
                    <div class="inventory-product-name">{{ p.name }}</div>
                    <div class="inventory-product-meta d-flex flex-wrap align-center">
                      <v-chip
                        small
                        label
                        outlined
                        class="inventory-category-chip text-none mr-2 mb-1"
                        color="secondary"
                      >
                        {{ p.category || 'Uncategorized' }}
                      </v-chip>
                      <span class="list-price inventory-price-pill mb-1">{{ formatZar(p.price) }}</span>
                      <span class="inventory-stock-pill text-body-2 mb-1 ml-2">
                        Stock
                        <strong class="text--primary">{{ p.stock != null ? p.stock : 0 }}</strong>
                      </span>
                    </div>
                  </div>
                </div>

                <div class="admin-inventory-card__actions">
                  <div class="inventory-actions-bar inventory-actions-bar--edit">
                    <v-btn
                      depressed
                      outlined
                      color="primary"
                      height="44"
                      class="inventory-btn-edit text-none font-weight-bold px-4"
                      :disabled="deletingId === p.id || editProductSaving"
                      @click="openProductEdit(p)"
                    >
                      <v-icon left small color="primary">edit</v-icon>
                      Edit product
                    </v-btn>
                    <v-btn
                      depressed
                      color="error"
                      height="44"
                      class="inventory-btn-delete text-none white--text px-4"
                      :disabled="deletingId === p.id || editProductSaving"
                      :loading="deletingId === p.id"
                      @click="openDeleteConfirm(p)"
                    >
                      <v-icon left small color="white">delete</v-icon>
                      Remove
                    </v-btn>
                  </div>
                </div>
                  </div>
                </div>
              </div>

              <div
                v-if="filteredInventoryProducts.length"
                class="inventory-pagination-row d-flex flex-column flex-sm-row align-center justify-space-between gap-3 pt-3"
              >
                <v-select
                  v-model="inventoryPerPage"
                  :items="inventoryPerPageOptions"
                  label="Items per page"
                  outlined
                  dense
                  hide-details
                  class="pagination-size-select rounded-lg mb-0"
                />
                <v-pagination
                  v-if="inventoryPageCount > 1"
                  v-model="inventoryPage"
                  :length="inventoryPageCount"
                  :total-visible="paginationVisible"
                  color="primary"
                  class="admin-pagination flex-grow-1 justify-sm-end"
                />
              </div>
            </template>
          </v-card>
      </v-col>
    </v-row>

    <v-dialog
      v-model="productEditDialog"
      max-width="560"
      content-class="rounded-xl"
      scrollable
      :persistent="editProductSaving"
      @click:outside="closeProductEditIfIdle"
    >
      <v-card v-if="productEdit" class="product-edit-dialog rounded-xl">
        <v-card-title class="text-h6 font-weight-bold pb-2">Edit product</v-card-title>
        <v-card-text class="pt-2">
          <p class="text-body-2 text--secondary mb-4">
            Update name, price, category, stock, or replace the product image. Changes apply to the shop immediately.
          </p>
          <v-text-field
            v-model="editProductName"
            outlined
            hide-details="auto"
            label="Product name"
            class="rounded-lg mb-3"
            :disabled="editProductSaving"
          />
          <v-text-field
            v-model="editProductCategory"
            outlined
            hide-details="auto"
            label="Category"
            hint="Used for filters on the shop."
            persistent-hint
            class="rounded-lg mb-2"
            :disabled="editProductSaving"
          />
          <v-text-field
            v-model="editProductPrice"
            outlined
            hide-details="auto"
            label="Price (ZAR)"
            type="number"
            min="0"
            step="0.01"
            prefix="R"
            class="rounded-lg mb-3"
            :disabled="editProductSaving"
          />
          <v-text-field
            v-model.number="editProductStock"
            outlined
            hide-details="auto"
            label="Stock quantity"
            type="number"
            min="0"
            class="rounded-lg mb-4"
            :disabled="editProductSaving"
          />
          <div class="text-subtitle-2 font-weight-bold mb-2">Image</div>
          <div v-if="productEdit.imageUrl || editProductImageFile" class="mb-3">
            <v-img
              v-if="editProductImageDisplaySrc"
              :src="editProductImageDisplaySrc"
              max-height="160"
              contain
              class="rounded-lg grey lighten-4"
            />
          </div>
          <v-file-input
            :key="'img-' + productEdit.id"
            v-model="editProductImageFile"
            accept="image/png,image/jpeg,image/webp,image/gif"
            label="Replace image (optional)"
            prepend-icon="image"
            outlined
            hide-details="auto"
            show-size
            class="rounded-lg"
            :disabled="editProductSaving"
            @change="editProductError = ''"
          />
          <v-alert v-if="editProductError" type="error" dense outlined class="mt-4 rounded-lg mb-0">
            {{ editProductError }}
          </v-alert>
        </v-card-text>
        <v-card-actions class="px-4 pb-4 pt-0 flex-wrap">
          <v-btn text class="text-none" :disabled="editProductSaving" @click="closeProductEditIfIdle"> Cancel </v-btn>
          <v-spacer />
          <v-btn
            depressed
            color="primary"
            class="text-none font-weight-bold btn-amber"
            :loading="editProductSaving"
            @click="saveProductEdit"
          >
            Save product
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="deleteDialogOpen"
      max-width="460"
      content-class="delete-dialog-surface"
      :persistent="Boolean(deletingId)"
      @click:outside="closeDeleteDialogIfIdle"
    >
      <v-card v-if="deleteTarget" class="delete-dialog-card rounded-xl" elevation="10">
        <v-card-text class="pa-8 pb-4">
          <div class="delete-dialog-icon-wrap mb-6" aria-hidden="true">
            <v-icon color="white" size="32">delete_outline</v-icon>
          </div>
          <h2 class="delete-dialog-title mb-3">Remove this product?</h2>
          <p class="delete-dialog-lead text-body-1 mb-4">
            <strong class="delete-dialog-name">{{ deleteTarget.name }}</strong> will be removed from the shop catalogue.
            Past orders that include this product are kept; the listing cannot be restored except by re-adding a product.
          </p>
          <v-alert type="warning" outlined prominent border="left" colored-border class="delete-dialog-alert rounded-lg mb-0">
            <span class="text-body-2">Please confirm you selected the correct product before continuing.</span>
          </v-alert>
        </v-card-text>
        <v-card-actions class="delete-dialog-actions px-8 pb-8 pt-0 flex-wrap">
          <v-btn
            large
            outlined
            rounded
            color="primary"
            class="text-none font-weight-bold mb-2"
            :disabled="Boolean(deletingId)"
            @click="closeDeleteDialogIfIdle"
          >
            Cancel
          </v-btn>
          <v-spacer />
          <v-btn
            large
            depressed
            rounded
            color="error"
            class="text-none font-weight-bold delete-dialog-confirm mb-2 white--text"
            :loading="deletingId === deleteTarget.id"
            @click="confirmDeleteProduct"
          >
            <v-icon left color="white">delete_forever</v-icon>
            Remove product
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script>
import adminModuleMixin from './mixins/adminModuleMixin'

export default {
  name: 'AdminProductsView',
  mixins: [adminModuleMixin]
}
</script>
