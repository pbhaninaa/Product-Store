import { describe, expect, it } from 'vitest'
import fs from 'fs'
import path from 'path'
import {
  ORDER_PAY_METHODS,
  SERVICE_PAY_METHODS,
  describeOrderPay,
  describeServicePay,
  incomeSplit,
  incomeSummaryRows,
  isPercentMethod,
  normalizeOrderPayMethod,
  orderPayItems,
  payKindLabel,
  payrollSplit,
  rateFieldLabel,
  rateRules,
  servicePayItems
} from '@/utils/employeePay'
import { orderPayMethodLabel, payMethodLabel } from '@/utils/displayLabels'
import { formatZar } from '@/utils/price'
import { rules } from '@/utils/validation'
import { friendlyError } from '@/utils/errorMessages'

const read = (rel) => fs.readFileSync(path.resolve(__dirname, '../../src/views/admin', rel), 'utf8')
const run = (list, v) => list.map((r) => r(v)).find((r) => r !== true) ?? true

describe('pay setting options', () => {
  it('offers % of service price for services and none / fixed / % for orders', () => {
    expect(SERVICE_PAY_METHODS).toContain('PERCENT_OF_SERVICE')
    expect(ORDER_PAY_METHODS).toEqual(['NONE', 'FIXED_PER_ORDER', 'PERCENT_OF_ORDER'])
    expect(servicePayItems().find((i) => i.value === 'PERCENT_OF_SERVICE').text).toBe('% of service price')
    expect(orderPayItems().map((i) => i.text)).toEqual(['No order pay', 'Fixed amount per order', '% of order total'])
    expect(payMethodLabel('PER_SERVICE')).toBe('Per service')
  })

  it('reads an unset order pay setting as none', () => {
    expect(normalizeOrderPayMethod(undefined)).toBe('NONE')
    expect(normalizeOrderPayMethod('')).toBe('NONE')
    expect(normalizeOrderPayMethod('percent_of_order')).toBe('PERCENT_OF_ORDER')
    expect(orderPayMethodLabel(null)).toBe('No order pay')
  })
})

describe('rate field', () => {
  it('switches between rand and percentage', () => {
    expect(isPercentMethod('PERCENT_OF_SERVICE')).toBe(true)
    expect(isPercentMethod('PER_SERVICE')).toBe(false)
    expect(rateFieldLabel('PER_HOUR')).toBe('Pay rate (ZAR) *')
    expect(rateFieldLabel('PERCENT_OF_ORDER', 'Order pay rate')).toBe('Order pay rate (%) *')
  })

  it('validates percentages 0 to 100 with at most 2 decimals', () => {
    const r = rateRules('PERCENT_OF_ORDER', 'Order pay rate')
    expect(run(r, 0)).toBe(true)
    expect(run(r, 100)).toBe(true)
    expect(run(r, 12.34)).toBe(true)
    expect(run(r, '12,5')).toBe(true)
    expect(run(r, 100.01)).toBe('Percentage must be between 0 and 100')
    expect(run(r, -1)).toBe('Percentage must be between 0 and 100')
    expect(run(r, 12.345)).toBe('Use at most 2 decimal places')
    expect(run(r, '')).toBe('Order pay rate is required')
    expect(rules.percentRate()('abc')).toBe('Percentage must be a number')
  })

  it('validates fixed amounts as rand of R0 or more, and skips the rate when order pay is off', () => {
    const r = rateRules('FIXED_PER_ORDER', 'Order pay rate')
    expect(run(r, 0)).toBe(true)
    expect(run(r, 250.5)).toBe(true)
    expect(run(r, -5)).toBe('Amount must be at least R0')
    expect(rateRules('NONE')).toEqual([])
    expect(run(rateRules('PER_SERVICE'), 150)).toBe(true)
  })
})

describe('pay descriptions', () => {
  it('describes service pay and order pay', () => {
    expect(describeServicePay('PER_SERVICE', 100)).toBe(`Per service, ${formatZar(100)}`)
    expect(describeServicePay('PERCENT_OF_SERVICE', 40)).toBe('40% of service price')
    expect(describeServicePay('PERCENT_OF_SERVICE', 12.5)).toBe('12.5% of service price')
    expect(describeOrderPay('FIXED_PER_ORDER', 20)).toBe(`${formatZar(20)} per order`)
    expect(describeOrderPay('PERCENT_OF_ORDER', 7.25)).toBe('7.25% of order total')
    expect(describeOrderPay(null, null)).toBe('No order pay')
    expect(payKindLabel('ORDER')).toBe('Order pay')
    expect(payKindLabel('SALON_BOOKING')).toBe('Service pay')
  })
})

describe('split totals', () => {
  const bundle = {
    owedTotal: 126.26,
    owedServiceTotal: 100,
    owedOrderTotal: 26.26,
    upcomingTotal: 80,
    upcomingServiceTotal: 0,
    upcomingOrderTotal: 80,
    paidOutTotal: 150,
    paidOutServiceTotal: 100,
    paidOutOrderTotal: 50
  }

  it('shows service pay, order pay and the total for owed, upcoming and paid out', () => {
    expect(incomeSummaryRows(bundle)).toEqual([
      { key: 'service', label: 'Service pay', owed: 100, upcoming: 0, paid: 100 },
      { key: 'order', label: 'Order pay', owed: 26.26, upcoming: 80, paid: 50 },
      { key: 'total', label: 'Total', owed: 126.26, upcoming: 80, paid: 150, isTotal: true }
    ])
  })

  it('treats an older response without the split as all service pay', () => {
    expect(incomeSplit({ owedTotal: 70 }, 'owed')).toEqual({ service: 70, order: 0, total: 70 })
    expect(incomeSummaryRows(null).map((r) => r.owed)).toEqual([0, 0, 0])
    expect(payrollSplit({ pendingExpected: 90 })).toEqual({ service: 90, order: 0, total: 90 })
    expect(payrollSplit({ pendingExpected: 126.26, servicePending: 100, orderPending: 26.26 })).toEqual({
      service: 100,
      order: 26.26,
      total: 126.26
    })
  })
})

describe('API error codes', () => {
  it('explains invalid pay settings', () => {
    expect(friendlyError('invalid_order_pay_rate')).toMatch(/0 to 100/)
    expect(friendlyError('invalid_pay_rate')).toMatch(/R0 or more/)
    expect(friendlyError('invalid_order_pay_method')).toMatch(/orders/)
  })
})

describe('screens', () => {
  it('the team form edits both settings and sends order pay', () => {
    const view = read('AdminTeamView.vue')
    expect(view).toMatch(/v-model="form\.orderPayMethod"/)
    expect(view).toMatch(/v-model\.number="form\.orderPayRate"/)
    expect(view).toMatch(/:rules="payRateRules"/)
    expect(view).toMatch(/orderPayMethod: 'NONE'/)
    expect(view).toMatch(/\.\.\.this\.payBody\(\)/)
    expect(view).toMatch(/text: 'Order pay', value: 'orderPayMethod'/)
  })

  it('payroll and My income show service pay and order pay separately plus a total', () => {
    const payroll = read('AdminTeamPayrollView.vue')
    expect(payroll).toMatch(/Service pay pending/)
    expect(payroll).toMatch(/Order pay pending/)
    expect(payroll).toMatch(/Total pending/)
    const income = read('AdminMyIncomeView.vue')
    expect(income).toMatch(/v-for="row in summaryRows"/)
    expect(income).toMatch(/incomeSummaryRows\(this\.bundle\)/)
  })
})
