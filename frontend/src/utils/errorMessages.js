/**
 * The API answers errors with short codes ("email_taken"). People must never see those, so
 * apiFetch turns them into these sentences; the raw code stays on `error.code` for logic.
 */
const MESSAGES = {
  // Sign-in and accounts
  not_authenticated: 'Please sign in to continue.',
  invalid_credentials: 'That email and password do not match.',
  account_suspended: 'This account has been suspended. Contact support for help.',
  owner_suspended: "This store's owner account is suspended. Contact support for help.",
  forbidden: 'You do not have access to do that.',
  permission_denied: "You don't have permission to do that. Ask the store owner.",
  email_taken: 'An account with this email already exists. Sign in instead.',
  email_already_exists: 'An account with this email already exists.',
  email_required: 'Enter an email address.',
  email_and_password_required: 'Enter your email and password.',
  email_mismatch: 'That email does not match our records.',
  invalid_email: 'Enter a valid email address.',
  invalid_password: 'That password is not valid.',
  password_too_short: 'Password must be at least 8 characters.',
  reset_invalid: 'This password reset link is invalid or has expired. Request a new one.',
  no_membership: 'This account is not linked to a store yet.',
  user_not_found: 'We could not find that user.',
  admin_missing: 'The system admin account is missing.',
  setup_required: 'The system admin account has not been created yet.',
  setup_complete: 'The system admin already exists.',
  cannot_suspend_self: 'You cannot suspend your own account.',

  // Stores and settings
  tenant_not_found: 'We could not find that store.',
  merchant_not_found: 'We could not find that store.',
  tenant_missing: 'We could not find your store.',
  tenant_required: 'Choose a store first.',
  merchant_owner_missing: 'This store has no owner account.',
  owner_missing: 'This store has no owner account.',
  merchant_slug_taken: 'That store link is already taken. Try another name.',
  slug_required: 'Enter a store link.',
  invalid_slug: 'Store links can use letters, numbers and dashes only.',
  slug_allocation_exhausted: 'We could not create a store link from that name. Try a different name.',
  invalid_business_name: 'Enter a business name with at least 2 letters or numbers.',
  invalid_name: 'Enter a valid name.',
  invalid_phone: 'Enter a valid phone number.',
  invalid_location: 'Choose a valid location.',
  location_required: 'Choose a location.',
  invalid_opening_hours_json: 'Check your opening hours and try again.',
  settings_not_found: 'Store settings were not found.',
  feature_disabled: 'This feature is switched off right now.',
  salon_not_enabled: 'Bookings are not available for this store.',
  no_updates: 'There is nothing to save.',

  // Products, promotions and staff
  product_not_found: 'That product is no longer available.',
  product_limit_reached: 'You have reached the product limit for your plan. Upgrade to add more.',
  promotion_not_found: 'That promotion no longer exists.',
  invalid_promo: 'That promotion is not valid.',
  employee_not_found: 'That team member was not found.',
  invalid_permission: 'One of those permissions is not recognised. Refresh the page and try again.',
  employee_limit_reached: 'You have reached the team size limit for your plan. Upgrade to add more.',
  staff_not_found: 'That staff member was not found.',
  support_user_not_found: 'That support user was not found.',
  invalid_pay_method: 'Choose how this team member is paid.',
  invalid_pay_rate: 'Enter a pay rate of R0 or more, or a percentage from 0 to 100 with at most 2 decimals.',
  invalid_order_pay_method: 'Choose how this team member is paid for orders.',
  invalid_order_pay_rate:
    'Enter an order pay amount of R0 or more, or a percentage from 0 to 100 with at most 2 decimals.',
  invalid_job_type: 'Choose a valid job type.',
  job_not_attributed: 'This job is not linked to a team member.',
  payroll_requires_plan: 'Team payroll is available on a higher plan.',
  insights_requires_plan: 'Insights are available on a higher plan.',
  permissions_required: 'Choose at least one permission.',

  // Cart, orders and checkout
  empty_cart: 'Your cart is empty.',
  invalid_line: 'One of the items in your cart is not valid.',
  too_many_lines: 'Your cart has too many different items.',
  insufficient_stock: 'Some items do not have enough stock left.',
  insufficient_stock_on_confirm: 'There is not enough stock left to confirm this order.',
  order_not_found: 'We could not find that order.',
  invalid_order: 'Enter a valid order reference.',
  order_not_pending: 'This order is no longer waiting for payment.',
  order_not_paid: 'This order has not been paid yet.',
  order_cancelled: 'This order was cancelled.',
  invalid_status: 'That status change is not allowed.',
  invalid_fulfillment_status: 'That status change is not allowed.',
  invalid_delivery_type: 'Choose pickup or delivery.',
  delivery_address_required: 'Enter a delivery address.',
  not_found: 'We could not find a match. Check the reference and email and try again.',
  already_rated: 'You have already left a review for this.',
  invalid_rating: 'Choose a rating from 1 to 5 stars.',
  confirm_required: 'Please confirm to continue.',
  not_completed: 'This is not completed yet.',

  // Payments
  invalid_payment_method: 'Choose a payment method.',
  payment_method_required: 'Choose a payment method.',
  payment_options_required: 'Turn on at least one payment option.',
  payment_not_configured: 'Payments are not set up for this store yet.',
  payment_verification_required: 'Check the proof of payment before confirming.',
  in_app_payment_not_received: 'The online payment has not come through yet.',
  handover_code_required: "Enter the customer's handover code.",
  invalid_handover_code: "That code doesn't match. Ask the customer to check the code on their order or booking.",
  handover_code_unavailable: 'This order or booking has no handover code. Contact support.',
  invalid_amount: 'Enter a valid amount.',
  eft_not_accepted: 'Bank transfer is no longer available. Pay online (card or Instant EFT) or in store.',
  cash_not_accepted: 'This store does not accept cash.',
  not_eft_order: 'This order was not paid by bank transfer.',
  not_eft_booking: 'This booking was not paid by bank transfer.',
  owner_email_missing: "Add a valid email to the store owner's account before paying online.",
  already_paid: 'This has already been paid. Refresh the page to see the latest status.',
  reference_mismatch:'That payment reference does not belong to this order. Open the link from your payment again.',
  email_or_reference_required: 'We could not identify this payment. Open the link from your payment again or use the Track page.',
  not_online_order: 'This order was not set up for online payment.',
  not_online_booking: 'This booking was not set up for online payment.',
  online_payment_not_accepted: 'This store does not accept online payment. Choose another way to pay.',
  online_payment_unavailable: 'Online payment is not available right now. Try again later or choose another way to pay.',
  online_payment_channel_required: 'Choose card or Instant EFT.',
  invalid_online_payment_channel: 'Choose card or Instant EFT.',
  online_payment_channel_not_applicable: 'Card or Instant EFT only applies when you pay online.',
  proof_missing: 'No proof of payment has been uploaded.',
  eft_proof_amount_or_reference_mismatch:
    'The proof does not match this payment’s amount or reference. Check your bank before approving.',
  proof_not_found: 'No proof of payment has been uploaded.',
  invalid_image: 'That image could not be read. Upload a real photo or screenshot (JPG or PNG), or a PDF.',
  rate_limited: 'Too many attempts. Please wait a few minutes and try again.',
  promotion_items_required: 'Choose at least one product or service for this promotion.',
  promotion_item_not_found: 'One of the chosen products or services no longer exists. Pick again.',
  promotion_too_many_items: 'A promotion can cover up to 200 products and services.',
  availability_overlap: 'This person already has a window that overlaps that time on that day.',
  availability_end_before_start: 'End time must be after the start time.',
  invalid_day_of_week: 'Choose a weekday.',
  invalid_pdf: 'That PDF could not be read. Try another file.',
  upload_invalid: 'That file could not be uploaded. Try another file.',
  image_too_large: 'Images must be 5 MB or smaller.',
  image_type_not_supported: 'Upload a JPG, PNG, WebP or GIF image.',
  not_pending_manual_review: 'This payment is not waiting for review.',
  invalid_decision: 'Choose approve or reject.',

  // Bookings
  booking_not_found: 'We could not find that booking.',
  invalid_booking: 'Enter a valid booking reference.',
  booking_not_pending: 'This booking is no longer waiting for payment.',
  booking_not_confirmed: 'This booking has not been confirmed yet.',
  service_not_found: 'That service is no longer available.',
  invalid_service: 'Choose a valid service.',
  invalid_duration: 'Enter a valid duration.',
  slot_unavailable: 'That time is no longer available. Choose another time.',
  slot_full: 'That time is fully booked. Choose another time.',
  slot_in_past: 'That time has already passed. Choose a later time.',
  no_staff: 'No staff are available for this service.',
  no_availability: 'There are no open times for this day.',
  invalid_date: 'Choose a valid date.',
  invalid_start: 'Choose a valid start time.',
  provide_both_start_and_end_or_neither: 'Enter both a start and end date, or neither.',

  // Booking fee
  booking_fee_percent_out_of_range: 'The booking fee must be a whole number from 0% to 50%.',
  booking_fee_unpaid:
    'The customer has not paid the booking fee yet. They can pay it from their booking, or the booking is cancelled after 2 hours.',
  booking_fee_paid_cancel_first:
    'This booking has a paid booking fee. Cancel the booking first so the fee is refunded, then delete it.',
  booking_not_cancellable: 'This booking can no longer be cancelled here. Contact the store for help.',
  no_show_too_early: 'You can mark a no show only after the booking start time.',

  // Plans and subscriptions
  plan_missing: 'Choose a plan.',
  plan_not_found: 'That plan was not found.',
  select_plan_first: 'Choose a plan first.',
  tier_required: 'Choose a plan.',
  invalid_plan_tier: 'Choose a valid plan.',
  invalid_subscription_plan: 'Choose a valid plan.',
  subscription_not_found: 'No subscription was found for this store.',
  subscription_inactive: 'Your subscription is not active. Renew it to continue.',
  trial_still_active: 'Your free trial is still active.',
  already_active_for_current_plan: 'This plan is already active.',
  subscription_proof_mutation_disabled: 'Subscription payments cannot be changed right now.',
  manual_subscription_activation_disabled: 'Manual plan activation is switched off.',
  platform_banking_mutation_disabled: 'Banking details cannot be changed right now.',

  // Support
  ticket_not_found: 'That help ticket was not found.',
  subject_required: 'Enter a subject.',
  body_required: 'Enter a message.',
  kind_required: 'Choose a type.',
  invalid_kind: 'Choose a valid type.',

  // Provider payouts and platform commission (support console)
  commission_invalid: 'Commission must be between 0% and 50%',
  payout_reference_required: 'Payment reference is required',
  payout_reference_too_long: 'Payment reference must be 100 characters or fewer.',
  payout_note_too_long: 'Note must be 500 characters or fewer.',
  payout_fee_invalid: 'Enter a fee total of R0 or more, in rand and cents.',
  payout_net_not_positive: 'The fee total and commission leave nothing to pay out. Check the fee total.',
  payout_nothing_owed: 'Nothing is owed to this store right now. Refresh to see the latest amounts.',
  payout_already_released: 'These payments have already been released',
  payout_amounts_changed: 'Amounts have changed, please refresh',

  // Generic
  validation_error: 'Check the highlighted fields and try again.',
  bad_request: 'Something in the request was not right. Check your details and try again.',
  conflict: 'That could not be done right now. Refresh the page and try again.',
  io_failed: 'Something went wrong while saving. Try again.'
}

/** Payment-provider checks (amount/currency/reference mismatches) share one message. */
const PAYMENT_PROVIDER_CHECK = /^online_payment_(amount|currency|reference)_/

/** True for API error codes like "email_taken" (lower-case words joined by underscores). */
export function isErrorCode(text) {
  return /^[a-z][a-z0-9]*(_[a-z0-9]+)+$|^[a-z]+$/.test(String(text || '').trim())
}

/** A sentence for an API error code; anything that is not a code is returned unchanged. */
export function friendlyError(codeOrText, fallback = 'Something went wrong. Please try again.') {
  const raw = String(codeOrText || '').trim()
  if (!raw) return fallback
  if (MESSAGES[raw]) return MESSAGES[raw]
  if (PAYMENT_PROVIDER_CHECK.test(raw)) return 'The payment could not be verified. Contact the store for help.'
  if (isErrorCode(raw)) return fallback
  return raw
}
