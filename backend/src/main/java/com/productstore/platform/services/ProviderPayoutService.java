package com.productstore.platform.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.ProviderPayoutEntity;
import com.productstore.platform.entities.ProviderPayoutLineEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.models.PayoutDtos.Line;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.services.ProviderEarningsService.Owed;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.util.RefCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Provider payouts for the platform admin: what each merchant is owed, the release screen, recording a
 * release, and the payout history. A release is recorded only; the app never moves money (BR-7).
 *
 * <p>Double release (BR-6, NFR-2, ADR-0001): the release transaction (READ_COMMITTED) first locks the
 * merchant row, so releases of one merchant take turns and the second one sees the first one's lines and
 * answers {@code payout_already_released}. The unique payment key on payout lines is the database backstop;
 * its violation, and a lock timeout or deadlock, are answered the same way, and nothing is recorded.
 */
@Service
public class ProviderPayoutService {
  public static final int REFERENCE_MAX = 100;
  public static final int NOTE_MAX = 500;
  public static final int DEFAULT_PAGE_SIZE = 50;
  public static final int MAX_PAGE_SIZE = 200;

  static final String ALREADY_RELEASED = "payout_already_released";
  static final String AMOUNTS_CHANGED = "payout_amounts_changed";

  private static final Logger log = LoggerFactory.getLogger(ProviderPayoutService.class);

  /**
   * A release as the admin confirmed it: the payments and version shown on the release screen, the fee total
   * (null = the captured fees), the payment reference and an optional internal note.
   */
  public record ReleaseRequest(
      UUID tenantId, List<UUID> attemptIds, String payableVersion, Long feeTotalCents, String reference, String note) {}

  private final TenantRepository tenants;
  private final ShopSettingsRepository shopSettings;
  private final ProviderPayoutRepository payouts;
  private final ProviderPayoutLineRepository payoutLines;
  private final ProviderEarningsService earnings;
  private final SupportAuditService audit;
  private final TransactionTemplate releaseTx;

  public ProviderPayoutService(
      TenantRepository tenants,
      ShopSettingsRepository shopSettings,
      ProviderPayoutRepository payouts,
      ProviderPayoutLineRepository payoutLines,
      ProviderEarningsService earnings,
      SupportAuditService audit,
      PlatformTransactionManager transactionManager) {
    this.tenants = tenants;
    this.shopSettings = shopSettings;
    this.payouts = payouts;
    this.payoutLines = payoutLines;
    this.earnings = earnings;
    this.audit = audit;
    this.releaseTx = new TransactionTemplate(transactionManager);
    this.releaseTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  // ------------------------------------------------------------------ owed

  /** Every merchant with something owed, largest net first; merchants with nothing owed are left out. */
  public PayoutDtos.OwedList owedList() {
    ProviderEarningsService.OwedAll owedAll = earnings.owedByMerchant();
    List<Owed> all = owedAll.merchants();
    Map<UUID, TenantEntity> byId = tenantsById(all.stream().map(Owed::tenantId).toList());
    List<PayoutDtos.OwedMerchant> merchants =
        all.stream()
            .map(
                o -> {
                  TenantEntity t = byId.get(o.tenantId());
                  var s = o.summary();
                  return new PayoutDtos.OwedMerchant(
                      o.tenantId(),
                      t == null ? null : t.name,
                      t == null ? null : t.slug,
                      s.paymentCount(),
                      s.grossCents(),
                      s.knownFeeCents(),
                      s.unknownFeeCount(),
                      s.commissionCents(),
                      s.netOwedCents(),
                      o.lines().get(0).paidAt());
                })
            .sorted(
                Comparator.comparingLong(PayoutDtos.OwedMerchant::netOwedCents)
                    .reversed()
                    .thenComparing(m -> m.tenantId().toString()))
            .toList();
    int count = 0;
    long gross = 0;
    long fee = 0;
    long commission = 0;
    long net = 0;
    for (PayoutDtos.OwedMerchant m : merchants) {
      count += m.paymentCount();
      gross += m.grossCents();
      fee += m.knownFeeCents();
      commission += m.commissionCents();
      net += m.netOwedCents();
    }
    int bp = owedAll.basisPoints();
    return new PayoutDtos.OwedList(
        bp,
        PlatformCommissionService.percent(bp),
        merchants,
        new PayoutDtos.OwedTotals(count, gross, fee, commission, net));
  }

  /** The release screen of one merchant: bank details, every owed payment, totals and the version. */
  public PayoutDtos.ReleasePreview preview(UUID tenantId) {
    TenantEntity tenant =
        (tenantId == null ? null : tenants.findById(tenantId).orElse(null));
    if (tenant == null) throw new IllegalArgumentException("not_found");
    Owed owed = earnings.owedFor(tenant.id);
    ShopSettingsEntity s = shopSettings.findByTenantId(tenant.id).orElse(null);
    PayoutDtos.BankDetails bank =
        new PayoutDtos.BankDetails(
            s == null ? "" : nz(s.bankName),
            s == null ? "" : nz(s.bankAccountHolder),
            s == null ? "" : nz(s.bankAccountNumber),
            s == null ? "" : nz(s.bankBranchCode));
    List<String> missing = new ArrayList<>();
    if (bank.bankName().isBlank()) missing.add("bankName");
    if (bank.accountHolder().isBlank()) missing.add("accountHolder");
    if (bank.accountNumber().isBlank()) missing.add("accountNumber");
    if (bank.branchCode().isBlank()) missing.add("branchCode");
    return new PayoutDtos.ReleasePreview(
        new PayoutDtos.Merchant(tenant.id, tenant.name, tenant.slug),
        bank,
        missing,
        owed.basisPoints(),
        PlatformCommissionService.percent(owed.basisPoints()),
        owed.lines(),
        owed.summary(),
        owed.payableVersion());
  }

  // ------------------------------------------------------------------ release

  /**
   * Records that the admin paid the merchant all currently owed payments (A4). Nothing is recorded when
   * any check fails.
   *
   * @throws IllegalArgumentException {@code payout_reference_required}, {@code payout_reference_too_long},
   *     {@code payout_note_too_long}, {@code payout_fee_invalid}, {@code payout_nothing_owed}, {@code
   *     payout_net_not_positive}, {@code not_found} (400 / 404)
   * @throws IllegalStateException {@code payout_already_released} or {@code payout_amounts_changed} (409)
   */
  public PayoutDtos.PayoutDetail release(ReleaseRequest req, ApiUserPrincipal actor) {
    if (actor == null || actor.userId() == null) throw new IllegalArgumentException("not_authenticated");
    String reference = stripUnicodeSpace(req.reference());
    if (reference.isEmpty()) throw new IllegalArgumentException("payout_reference_required");
    if (reference.length() > REFERENCE_MAX) throw new IllegalArgumentException("payout_reference_too_long");
    String trimmedNote = stripUnicodeSpace(req.note());
    String note = trimmedNote.isEmpty() ? null : trimmedNote;
    if (note != null && note.length() > NOTE_MAX) throw new IllegalArgumentException("payout_note_too_long");
    if (req.feeTotalCents() != null && req.feeTotalCents() < 0) {
      throw new IllegalArgumentException("payout_fee_invalid");
    }
    if (req.tenantId() == null) throw new IllegalArgumentException("not_found");
    List<UUID> requested = req.attemptIds() == null ? List.of() : List.copyOf(req.attemptIds());

    try {
      return releaseTx.execute(status -> releaseLocked(req, requested, reference, note, actor));
    } catch (DataIntegrityViolationException e) {
      // The unique payment key: another release won the race. Anything else is a real error.
      if (!requested.isEmpty() && !payoutLines.findReleasedAttemptIds(requested).isEmpty()) {
        log.warn("Payout release for tenant {} lost a concurrent release (unique payment key)", req.tenantId());
        throw new IllegalStateException(ALREADY_RELEASED);
      }
      throw e;
    } catch (PessimisticLockingFailureException e) {
      log.warn("Payout release for tenant {} could not lock the merchant: {}", req.tenantId(), e.getClass().getSimpleName());
      throw new IllegalStateException(ALREADY_RELEASED);
    }
  }

  private PayoutDtos.PayoutDetail releaseLocked(
      ReleaseRequest req, List<UUID> requested, String reference, String note, ApiUserPrincipal actor) {
    // First statement: serialises releases of this merchant.
    TenantEntity tenant = tenants.lockById(req.tenantId()).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (!requested.isEmpty() && !payoutLines.findReleasedAttemptIds(requested).isEmpty()) {
      throw new IllegalStateException(ALREADY_RELEASED);
    }
    Owed owed = earnings.owedFor(tenant.id);
    if (owed.lines().isEmpty()) throw new IllegalArgumentException("payout_nothing_owed");
    Set<UUID> current = owed.lines().stream().map(Line::attemptId).collect(Collectors.toSet());
    if (requested.size() != current.size()
        || !current.equals(new HashSet<>(requested))
        || !owed.payableVersion().equals(req.payableVersion())) {
      throw new IllegalStateException(AMOUNTS_CHANGED);
    }

    var sum = owed.summary();
    long feeUsed = req.feeTotalCents() != null ? req.feeTotalCents() : sum.knownFeeCents();
    long net = sum.grossCents() - feeUsed - sum.commissionCents();
    if (net <= 0) throw new IllegalArgumentException("payout_net_not_positive");

    ProviderPayoutEntity p = new ProviderPayoutEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenant.id;
    p.paymentCount = sum.paymentCount();
    p.grossCents = sum.grossCents();
    p.capturedFeeCents = sum.knownFeeCents();
    p.unknownFeeCount = sum.unknownFeeCount();
    p.feeCents = feeUsed;
    p.feeAdjusted = feeUsed != sum.knownFeeCents();
    p.commissionBasisPoints = owed.basisPoints();
    p.commissionCents = sum.commissionCents();
    p.netCents = net;
    p.reference = reference;
    p.note = note;
    p.releasedByUserId = actor.userId();
    p.releasedByEmail = actor.getUsername() == null ? "" : actor.getUsername();
    payouts.saveAndFlush(p);

    List<ProviderPayoutLineEntity> lines = new ArrayList<>(owed.lines().size());
    for (Line l : owed.lines()) {
      ProviderPayoutLineEntity e = new ProviderPayoutLineEntity();
      e.id = UUID.randomUUID();
      e.payoutId = p.id;
      e.paymentAttemptId = l.attemptId();
      e.tenantId = tenant.id;
      e.kind = OnlinePaymentAttemptEntity.Kind.valueOf(l.kind());
      e.recordId = l.recordId();
      e.paymentReference = l.paymentReference();
      e.paidAt = l.paidAt();
      e.grossCents = l.grossCents();
      e.feeCents = l.feeCents();
      e.commissionCents = l.commissionCents();
      lines.add(e);
    }
    payoutLines.saveAllAndFlush(lines);

    // Same transaction: the release and its audit entry commit or roll back together. No bank details.
    audit.record(actor, "PAYOUT_RELEASE", "PAYOUT", p.id.toString(), auditDetail(tenant, p));
    log.info(
        "Payout {} recorded for merchant {}: {} payments, net {} cents", p.id, tenant.slug, p.paymentCount, p.netCents);
    return new PayoutDtos.PayoutDetail(toDto(p, tenant), lines.stream().map(ProviderPayoutService::toLine).toList());
  }

  static String auditDetail(TenantEntity tenant, ProviderPayoutEntity p) {
    return "merchant="
        + tenant.slug
        + ";tenantId="
        + tenant.id
        + ";payments="
        + p.paymentCount
        + ";grossCents="
        + p.grossCents
        + ";feeCents="
        + p.feeCents
        + ";feeAdjusted="
        + p.feeAdjusted
        + ";commissionBp="
        + p.commissionBasisPoints
        + ";commissionCents="
        + p.commissionCents
        + ";netCents="
        + p.netCents
        + ";reference="
        + p.reference.replaceAll("[;\\r\\n]", ",");
  }

  // ------------------------------------------------------------------ history

  /** Released payouts, newest first; dates are SAST calendar days on the release time, both or neither. */
  public PayoutDtos.PayoutPage history(UUID tenantId, LocalDate start, LocalDate end, Integer page, Integer size) {
    ProviderEarningsService.requireBothOrNeither(start, end);
    int p = page == null ? 0 : Math.max(0, page);
    int s = size == null ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    PageRequest pr =
        PageRequest.of(p, s, Sort.by(Sort.Order.desc("releasedAt"), Sort.Order.desc("id")));
    Page<ProviderPayoutEntity> result;
    if (start == null) {
      result = tenantId == null ? payouts.findAll(pr) : payouts.findByTenantId(tenantId, pr);
    } else {
      var from = ProviderEarningsService.startOfDay(start);
      var to = ProviderEarningsService.startOfDay(end.plusDays(1));
      result =
          tenantId == null
              ? payouts.findByReleasedAtGreaterThanEqualAndReleasedAtLessThan(from, to, pr)
              : payouts.findByTenantIdAndReleasedAtGreaterThanEqualAndReleasedAtLessThan(tenantId, from, to, pr);
    }
    Map<UUID, TenantEntity> byId = tenantsById(result.getContent().stream().map(x -> x.tenantId).toList());
    return new PayoutDtos.PayoutPage(
        result.getContent().stream().map(x -> toDto(x, byId.get(x.tenantId))).toList(),
        p,
        s,
        result.getTotalElements());
  }

  public PayoutDtos.PayoutDetail detail(UUID payoutId) {
    ProviderPayoutEntity p =
        (payoutId == null ? null : payouts.findById(payoutId).orElse(null));
    if (p == null) throw new IllegalArgumentException("not_found");
    TenantEntity t = tenants.findById(p.tenantId).orElse(null);
    List<Line> lines =
        payoutLines.findByPayoutIdOrderByPaidAtAscIdAsc(p.id).stream().map(ProviderPayoutService::toLine).toList();
    return new PayoutDtos.PayoutDetail(toDto(p, t), lines);
  }

  // ------------------------------------------------------------------ mapping

  static PayoutDtos.Payout toDto(ProviderPayoutEntity p, TenantEntity t) {
    return new PayoutDtos.Payout(
        p.id,
        p.tenantId,
        t == null ? null : t.name,
        t == null ? null : t.slug,
        p.releasedAt,
        p.paymentCount,
        p.grossCents,
        p.capturedFeeCents,
        p.unknownFeeCount,
        p.feeCents,
        p.feeAdjusted,
        p.commissionBasisPoints,
        PlatformCommissionService.percent(p.commissionBasisPoints),
        p.commissionCents,
        p.netCents,
        p.reference,
        p.note,
        p.releasedByEmail);
  }

  static Line toLine(ProviderPayoutLineEntity l) {
    return new Line(
        l.paymentAttemptId,
        l.kind.name(),
        l.recordId,
        RefCodes.shortRef(l.recordId),
        l.paymentReference,
        l.paidAt,
        l.grossCents,
        l.feeCents,
        l.commissionCents);
  }

  private Map<UUID, TenantEntity> tenantsById(Collection<UUID> ids) {
    if (ids.isEmpty()) return Map.of();
    return tenants.findAllById(new HashSet<>(ids)).stream()
        .collect(Collectors.toMap(t -> t.id, Function.identity()));
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  /**
   * Trims every kind of Unicode space (including no-break and ideographic spaces), which
   * {@link String#trim()} and {@link String#strip()} miss, so a reference of only spaces counts as blank.
   */
  static String stripUnicodeSpace(String value) {
    return value == null ? "" : value.replaceAll("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$", "");
  }
}
