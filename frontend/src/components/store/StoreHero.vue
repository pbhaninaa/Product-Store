<template>
  <section class="admin-hero store-hero">
    <v-container class="py-6 py-md-10 px-3 px-sm-4">
      <v-row align="center" class="admin-hero__inner">
        <v-col cols="12" :md="copyCols" class="pr-md-8 min-width-0">
          <slot name="top" />
          <div class="admin-kicker store-hero__kicker mb-3">
            <span class="store-hero__dot" aria-hidden="true" />
            {{ kicker }}
          </div>
          <h1 class="admin-title store-hero__title">
            <slot name="title" />
          </h1>
          <slot />
        </v-col>

        <v-col
          cols="12"
          :md="12 - copyCols"
          :class="advertUrl ? 'd-flex justify-center mt-4 mt-md-0' : 'd-none d-md-flex justify-center'"
        >
          <div v-if="advertUrl" class="store-hero__visual store-hero__visual--advert">
            <img :src="advertUrl" alt="" class="store-hero__advert" loading="eager" />
          </div>
          <div v-else class="store-hero__visual" aria-hidden="true">
            <div class="store-hero__ring" />
            <div class="store-hero__card store-hero__card--1" />
            <div class="store-hero__card store-hero__card--2" />
            <div class="store-hero__badge">
              <v-icon color="white" small>local_offer</v-icon>
            </div>
          </div>
        </v-col>
      </v-row>
    </v-container>
  </section>
</template>

<script>
/**
 * Storefront header (store page, service booking): the admin page header band with the store's own advert
 * image, or decorative art when it has none. Copy goes in the slots: `top` (e.g. a back link), `title` (h1
 * content; wrap a highlighted phrase in .store-hero__accent) and the default slot (lead, search fields).
 */
export default {
  name: 'StoreHero',
  props: {
    kicker: { type: String, required: true },
    advertUrl: { type: String, default: '' },
    /** The advert takes the wider column (store page); otherwise the copy does. */
    advertWide: { type: Boolean, default: false }
  },
  computed: {
    copyCols() {
      return this.advertUrl && this.advertWide ? 5 : 7
    }
  }
}
</script>

<!--
  Unscoped on purpose: the title, lead and search fields are slot content, which carries the page's scope, not
  this component's. Every rule is namespaced under .store-hero.
-->
<style>
.store-hero .store-hero__kicker {
  gap: 0.5rem;
}

.store-hero .store-hero__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  box-shadow: 0 0 0 4px rgba(234, 88, 12, 0.2);
}

.store-hero .store-hero__title {
  max-width: 20ch;
}

.store-hero .store-hero__accent {
  background: linear-gradient(120deg, #ea580c, #c2410c);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.store-hero .store-hero__search {
  width: 100%;
  max-width: 440px;
}

.store-hero .store-hero__field .v-input__slot {
  border-radius: 16px !important;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 12px 40px -16px rgba(15, 23, 42, 0.25) !important;
  border: 1px solid rgba(15, 23, 42, 0.06) !important;
  padding-left: 4px !important;
  min-height: 56px !important;
}

.store-hero .store-hero__field input::placeholder {
  color: rgba(100, 116, 139, 0.85);
}

.store-hero .store-hero__meta {
  display: flex;
  align-items: center;
}

/* Decorative art keeps a fixed footprint so it never opens a wide empty column beside the copy. */
.store-hero .store-hero__visual {
  position: relative;
  width: 260px;
  max-width: 100%;
  height: 260px;
}

.store-hero .store-hero__visual--advert {
  width: 100%;
  height: auto;
}

.store-hero .store-hero__advert {
  width: 100%;
  max-height: 380px;
  object-fit: cover;
  border-radius: 20px;
  box-shadow: 0 24px 50px -20px rgba(15, 23, 42, 0.35);
}

.store-hero .store-hero__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px dashed rgba(15, 23, 42, 0.12);
  animation: store-hero-spin 28s linear infinite;
}

.store-hero .store-hero__card {
  position: absolute;
  border-radius: 20px;
  box-shadow: 0 24px 50px -20px rgba(15, 23, 42, 0.35);
}

.store-hero .store-hero__card--1 {
  width: 140px;
  height: 180px;
  left: 50%;
  top: 50%;
  transform: translate(-62%, -58%) rotate(-8deg);
  background: linear-gradient(160deg, #1e3a5f, #0f172a);
}

.store-hero .store-hero__card--2 {
  width: 120px;
  height: 150px;
  left: 50%;
  top: 50%;
  transform: translate(-22%, -32%) rotate(10deg);
  background: linear-gradient(160deg, #fed7aa, #fb923c);
}

.store-hero .store-hero__badge {
  position: absolute;
  right: 12%;
  top: 18%;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #ea580c, #c2410c);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12px 28px -10px rgba(194, 65, 12, 0.7);
}

@keyframes store-hero-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .store-hero .store-hero__ring {
    animation: none;
  }
}
</style>
