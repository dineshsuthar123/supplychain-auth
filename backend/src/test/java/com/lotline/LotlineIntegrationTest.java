package com.lotline;

import static org.assertj.core.api.Assertions.*;

import com.lotline.audit.*;
import com.lotline.catalog.*;
import com.lotline.custody.*;
import com.lotline.identity.*;
import com.lotline.inventory.*;
import com.lotline.lineage.*;
import com.lotline.notifications.*;
import com.lotline.recall.*;
import com.lotline.shared.*;
import com.lotline.workspace.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LotlineIntegrationTest {
  @Container
  static PostgreSQLContainer pg =
      new PostgreSQLContainer("postgres:17.9-bookworm").withInitScript("legacy.sql");

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("debug", () -> "false");
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.flyway.user", pg::getUsername);
    r.add("spring.flyway.password", pg::getPassword);
    r.add("lotline.jwt-secret", () -> "test-only-secret-over-32-characters-please");
    r.add("lotline.outbox-delay-ms", () -> "3600000");
  }

  @Autowired Db db;
  @Autowired Json json;
  @Autowired IdentityService auth;
  @Autowired WorkspaceService workspace;
  @Autowired CatalogService catalog;
  @Autowired InventoryService inventory;
  @Autowired CustodyService custody;
  @Autowired RecallService recalls;
  @Autowired Ledger ledger;
  @Autowired TraceService trace;
  @Autowired EvidenceService evidence;
  @Autowired OutboxWorker worker;
  @Autowired CommandBus bus;
  @Autowired ObservationService observations;
  @LocalServerPort int port;

  static String key() {
    return UUID.randomUUID().toString();
  }

  static UUID id(Map<String, Object> r) {
    return UUID.fromString(r.get("id").toString());
  }

  static final String PASSWORD = "Test-account-password-2026";

  class Network {
    final Member owner, partner;
    final UUID source, destination, product;
    final String email;
    final IdentityService.Issued issued;

    Network() {
      email = key() + "@example.test";
      issued =
          auth.register(
              new IdentityService.Register(
                  email, PASSWORD, "Mira Vale", "Test network", "Coffee Works"),
              key());
      owner = auth.authenticate(issued.body().accessToken());
      source =
          id(
              workspace.site(
                  owner,
                  key(),
                  new WorkspaceService.Site(owner.organizationId(), "Packing floor")));
      UUID org =
          id(
              workspace.organization(
                  owner, key(), new WorkspaceService.Organization("Harbor warehouse")));
      destination =
          id(workspace.site(owner, key(), new WorkspaceService.Site(org, "Harbor receiving")));
      String partnerEmail = key() + "@example.test";
      var invite =
          workspace.invite(
              owner,
              key(),
              new WorkspaceService.Invite(
                  org, partnerEmail, "PARTNER_OPERATOR", false, List.of(destination)));
      String token = invite.get("invitationPath").toString().split("token=")[1];
      partner =
          auth.authenticate(
              auth.accept(
                      new IdentityService.Accept(token, partnerEmail, PASSWORD, "Isha Receiver"))
                  .body()
                  .accessToken());
      product =
          id(
              catalog.create(
                  owner, key(), new CatalogService.Product("Coffee · 250 g package", "unit")));
    }

    UUID commission(String code, String quantity) {
      return id(
          inventory.commission(
              owner,
              key(),
              new InventoryService.Commission(
                  product, source, code, quantity, LocalDate.now(), null, null)));
    }

    Map<String, Object> draft(UUID lot, String qty, boolean reserve) {
      return custody.draft(
          owner,
          key(),
          new CustodyService.Draft(
              source,
              destination,
              "Dispatch claim",
              List.of(new CustodyService.Line(lot, qty)),
              reserve,
              null));
    }

    UUID ship(UUID lot, String qty) {
      UUID t = id(draft(lot, qty, false));
      custody.dispatch(owner, t, key(), new CustodyService.Dispatch(0, null));
      return t;
    }

    void receive(UUID t, UUID lot, String qty) {
      custody.receive(
          partner,
          t,
          key(),
          new CustodyService.Receipt(
              1, List.of(new CustodyService.Received(lot, qty, "GOOD", "Receiving count")), null));
    }

    UUID recall(UUID root) {
      var p = recalls.preview(owner, new RecallService.Preview(List.of(root)));
      return id(
          recalls.activate(
              owner,
              key(),
              new RecallService.Activate(
                  List.of(root),
                  "Packaging defect",
                  "Supplier seal fault",
                  ((Number) p.get("sequence")).longValue(),
                  true)));
    }

    void recover(Member actor, UUID lot, UUID site, String quantity) {
      inventory.disposition(
          actor,
          key(),
          new InventoryService.Disposition(
              lot,
              site,
              "RECOVER",
              "HELD",
              quantity,
              "Segregated stock",
              "Recovery register 14",
              null));
    }
  }

  @SuppressWarnings("unchecked")
  List<Map<String, Object>> rows(Object value) {
    return (List<Map<String, Object>>) value;
  }

  @Test
  void fresh_and_legacy_adjacent_migrations_preserve_public_data() throws Exception {
    assertThat(db.one("SELECT evidence FROM public.legacy_accounts WHERE id=7").get("evidence"))
        .isEqualTo("legacy data must remain byte-identical");
    assertThat(db.count("SELECT checksum FROM public.flyway_schema_history WHERE installed_rank=1"))
        .isEqualTo(123456789);
    assertThat(
            db.count(
                "SELECT count(*) FROM lotline.lotline_schema_history WHERE success=true AND version"
                    + " IS NOT NULL"))
        .isEqualTo(3);
    try (var c =
            java.sql.DriverManager.getConnection(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var s = c.createStatement()) {
      s.execute("CREATE DATABASE lotline_fresh_test");
    }
    String url = pg.getJdbcUrl().replace("/test", "/lotline_fresh_test");
    Flyway.configure()
        .dataSource(url, pg.getUsername(), pg.getPassword())
        .schemas("lotline")
        .defaultSchema("lotline")
        .table("lotline_schema_history")
        .locations("classpath:db/migration")
        .load()
        .migrate();
    try (var c = java.sql.DriverManager.getConnection(url, pg.getUsername(), pg.getPassword());
        var s = c.createStatement();
        var rs = s.executeQuery("SELECT count(*) FROM lotline.balances")) {
      rs.next();
      assertThat(rs.getLong(1)).isZero();
    }
  }

  @Test
  void invitation_is_hashed_bound_single_use_expiring_and_permissions_usable() {
    var n = new Network();
    UUID org =
        id(
            workspace.organization(
                n.owner, key(), new WorkspaceService.Organization("Restricted partner")));
    UUID site =
        id(workspace.site(n.owner, key(), new WorkspaceService.Site(org, "Only granted site")));
    String email = key() + "@example.test";
    var r = new WorkspaceService.Invite(org, email, "PARTNER_OPERATOR", false, List.of(site));
    String k = key();
    var inv = workspace.invite(n.owner, k, r);
    assertThat(workspace.invite(n.owner, k, r)).isEqualTo(inv);
    String token = inv.get("invitationPath").toString().split("token=")[1];
    assertThat(
            db.one(
                    "SELECT token_hash FROM invitations WHERE workspace_id=? AND id=?",
                    n.owner.workspaceId(),
                    id(inv))
                .get("token_hash"))
        .isEqualTo(Json.hash(token));
    assertThatThrownBy(
            () ->
                auth.accept(
                    new IdentityService.Accept(token, "wrong@example.test", PASSWORD, "Wrong")))
        .isInstanceOf(DomainError.class);
    var user = auth.accept(new IdentityService.Accept(token, email, PASSWORD, "Receiver"));
    var member = auth.authenticate(user.body().accessToken());
    assertThat(member.workspaceId()).isEqualTo(n.owner.workspaceId());
    assertThat(member.sites()).containsExactly(site);
    assertThatThrownBy(
            () -> auth.accept(new IdentityService.Accept(token, email, PASSWORD, "Receiver")))
        .extracting("code")
        .isEqualTo("INVITATION_EXPIRED");
    var exp =
        workspace.invite(
            n.owner,
            key(),
            new WorkspaceService.Invite(
                org, key() + "@example.test", "OPERATOR", false, List.of()));
    db.update(
        "UPDATE invitations SET expires_at=now()-interval '1 second' WHERE workspace_id=? AND id=?",
        n.owner.workspaceId(),
        id(exp));
    assertThatThrownBy(
            () ->
                auth.accept(
                    new IdentityService.Accept(
                        exp.get("invitationPath").toString().split("token=")[1],
                        "x@example.test",
                        PASSWORD,
                        "Expired")))
        .extracting("code")
        .isEqualTo("INVITATION_EXPIRED");
    assertThatThrownBy(
            () ->
                workspace.invite(
                    n.owner,
                    key(),
                    new WorkspaceService.Invite(org, email, "OWNER", false, List.of())))
        .extracting("code")
        .isEqualTo("INVITATION_ROLE");
  }

  @Test
  void quantities_partial_consumption_conservation_new_outputs_and_append_only() {
    var n = new Network();
    UUID root = n.commission("CF-2401", "1200");
    String k = key();
    var req =
        new InventoryService.Operation(
            "SPLIT",
            n.source,
            List.of(new InventoryService.Input(root, "700")),
            List.of(
                new InventoryService.Output("A", "350"), new InventoryService.Output("B", "340")),
            "10",
            null);
    var op = inventory.operation(n.owner, k, req);
    assertThat(inventory.operation(n.owner, k, req)).isEqualTo(op);
    assertThat(ledger.quantity(n.owner.workspaceId(), root, n.source, "AVAILABLE", null))
        .isEqualByComparingTo("500");
    assertThat(rows(op.get("outputs"))).hasSize(2);
    assertThat(db.count("SELECT count(*) FROM lineage WHERE workspace_id=?", n.owner.workspaceId()))
        .isEqualTo(2);
    assertThatThrownBy(
            () ->
                inventory.commission(
                    n.owner,
                    key(),
                    new InventoryService.Commission(
                        n.product, n.source, "precision", "1.0001", LocalDate.now(), null, null)))
        .extracting("code")
        .isEqualTo("QUANTITY_PRECISION");
    assertThatThrownBy(
            () ->
                inventory.operation(
                    n.owner,
                    key(),
                    new InventoryService.Operation(
                        "SPLIT",
                        n.source,
                        List.of(new InventoryService.Input(root, "100")),
                        List.of(new InventoryService.Output("bad", "101")),
                        "0",
                        null)))
        .extracting("code")
        .isEqualTo("CONSERVATION_FAILED");
    assertThatThrownBy(
            () ->
                db.update(
                    "UPDATE events SET type='rewritten' WHERE workspace_id=?",
                    n.owner.workspaceId()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(evidence.compareProjection(n.owner).get("matches")).isEqualTo(true);
    evidence.rebuild(n.owner, key(), bus);
    assertThat(evidence.compareProjection(n.owner).get("matches")).isEqualTo(true);
  }

  @Test
  void changed_body_rejects_persisted_key_and_rolls_back_sequence() {
    var n = new Network();
    UUID root = n.commission("root", "10");
    String k = key();
    var r =
        new CustodyService.Draft(
            n.source, n.destination, "R", List.of(new CustodyService.Line(root, "3")), false, null);
    var accepted = custody.draft(n.owner, k, r);
    long seq = db.count("SELECT sequence FROM workspaces WHERE id=?", n.owner.workspaceId());
    assertThat(custody.draft(n.owner, k, r)).isEqualTo(accepted);
    assertThatThrownBy(
            () ->
                custody.draft(
                    n.owner,
                    k,
                    new CustodyService.Draft(
                        n.source,
                        n.destination,
                        "R",
                        List.of(new CustodyService.Line(root, "4")),
                        false,
                        null)))
        .extracting("code")
        .isEqualTo("IDEMPOTENCY_CONFLICT");
    assertThat(db.count("SELECT sequence FROM workspaces WHERE id=?", n.owner.workspaceId()))
        .isEqualTo(seq);
  }

  @Test
  void short_receipt_is_destination_stock_plus_unresolved_claims_and_receiver_only() {
    var n = new Network();
    UUID lot = n.commission("B", "500"), t = n.ship(lot, "500");
    assertThatThrownBy(
            () ->
                custody.receive(
                    n.owner,
                    t,
                    key(),
                    new CustodyService.Receipt(
                        1,
                        List.of(new CustodyService.Received(lot, "480", "GOOD", "Short")),
                        null)))
        .extracting("code")
        .isEqualTo("RECEIVER_REQUIRED");
    assertThatThrownBy(() -> n.receive(t, lot, "501")).extracting("code").isEqualTo("OVER_RECEIPT");
    n.receive(t, lot, "480");
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "AVAILABLE", null))
        .isEqualByComparingTo("480");
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "UNRESOLVED", t))
        .isEqualByComparingTo("20");
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.source, "AVAILABLE", null)).isZero();
    assertThatThrownBy(() -> n.receive(t, lot, "480")).isInstanceOf(DomainError.class);
    assertThat(rows(custody.detail(n.partner, t).get("receipts"))).hasSize(1);
  }

  List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                gate.await();
                try {
                  return first.call();
                } catch (DomainError e) {
                  return e;
                }
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                try {
                  return second.call();
                } catch (DomainError e) {
                  return e;
                }
              });
      gate.countDown();
      return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
    }
  }

  @Test
  void simultaneous_dispatches_cannot_overspend() throws Exception {
    var n = new Network();
    UUID lot = n.commission("race", "1200"),
        a = id(n.draft(lot, "900", false)),
        b = id(n.draft(lot, "900", false));
    var result =
        race(
            () -> custody.dispatch(n.owner, a, key(), new CustodyService.Dispatch(0, null)),
            () -> custody.dispatch(n.owner, b, key(), new CustodyService.Dispatch(0, null)));
    assertThat(result.stream().filter(x -> x instanceof DomainError).count()).isEqualTo(1);
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.source, "AVAILABLE", null))
        .isEqualByComparingTo("300");
    assertThat(evidence.compareProjection(n.owner).get("matches")).isEqualTo(true);
  }

  @Test
  void concurrent_receipts_and_duplicate_idempotent_commands_never_duplicate_stock()
      throws Exception {
    var n = new Network();
    UUID lot = n.commission("race", "500"), t = n.ship(lot, "500");
    var r =
        new CustodyService.Receipt(
            1, List.of(new CustodyService.Received(lot, "480", "GOOD", "20 missing")), null);
    String k = key();
    var result =
        race(() -> custody.receive(n.partner, t, k, r), () -> custody.receive(n.partner, t, k, r));
    assertThat(result.get(0)).isEqualTo(result.get(1));
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "AVAILABLE", null))
        .isEqualByComparingTo("480");
    assertThat(
            db.count(
                "SELECT count(*) FROM receipts WHERE workspace_id=? AND transfer_id=?",
                n.owner.workspaceId(),
                t))
        .isEqualTo(1);
    UUID lot2 = n.commission("race2", "10"), t2 = n.ship(lot2, "10");
    var second =
        new CustodyService.Receipt(
            1, List.of(new CustodyService.Received(lot2, "10", "GOOD", "")), null);
    var distinct =
        race(
            () -> custody.receive(n.partner, t2, key(), second),
            () -> custody.receive(n.partner, t2, key(), second));
    assertThat(distinct.stream().filter(x -> x instanceof DomainError).count()).isEqualTo(1);
  }

  @Test
  void recall_dispatch_race_serializes_on_workspace_guard_and_rejects_stale_scope()
      throws Exception {
    for (int iteration = 0; iteration < 3; iteration++) {
      var n = new Network();
      UUID lot = n.commission("race", "50"), t = id(n.draft(lot, "50", false));
      var p = recalls.preview(n.owner, new RecallService.Preview(List.of(lot)));
      var r =
          new RecallService.Activate(
              List.of(lot),
              "Race incident",
              "Defect",
              ((Number) p.get("sequence")).longValue(),
              true);
      var result =
          race(
              () -> recalls.activate(n.owner, key(), r),
              () -> custody.dispatch(n.owner, t, key(), new CustodyService.Dispatch(0, null)));
      assertThat(result.stream().filter(x -> x instanceof DomainError).count()).isEqualTo(1);
      if (result.get(0) instanceof DomainError) {
        assertThat(((DomainError) result.get(0)).code).isEqualTo("STALE_PREVIEW");
        n.recall(lot);
        n.receive(t, lot, "50");
        assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "HELD", null))
            .isEqualByComparingTo("50");
      } else assertThat(((DomainError) result.get(1)).code).isEqualTo("RECALL_HOLD");
      assertThat(evidence.compareProjection(n.owner).get("matches")).isEqualTo(true);
    }
  }

  @Test
  void preexisting_reservation_is_held_and_in_transit_receipt_can_contain() {
    var n = new Network();
    UUID lot = n.commission("reserved", "100"),
        reserved = id(n.draft(lot, "50", true)),
        travel = n.ship(lot, "50");
    n.recall(lot);
    assertThatThrownBy(
            () -> custody.dispatch(n.owner, reserved, key(), new CustodyService.Dispatch(0, null)))
        .extracting("code")
        .isEqualTo("RECALL_HOLD");
    n.receive(travel, lot, "50");
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "HELD", null))
        .isEqualByComparingTo("50");
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.source, "HELD", null))
        .isEqualByComparingTo("50");
  }

  @Test
  void mixed_output_and_diamond_are_conservative_without_double_counting() {
    var n = new Network();
    UUID root = n.commission("affected", "100"), safe = n.commission("unrelated", "100");
    var split =
        inventory.operation(
            n.owner,
            key(),
            new InventoryService.Operation(
                "SPLIT",
                n.source,
                List.of(new InventoryService.Input(root, "100")),
                List.of(
                    new InventoryService.Output("A", "50"), new InventoryService.Output("B", "50")),
                "0",
                null));
    var o = rows(split.get("outputs"));
    UUID a = id(o.get(0)), b = id(o.get(1));
    var merge =
        inventory.operation(
            n.owner,
            key(),
            new InventoryService.Operation(
                "MERGE",
                n.source,
                List.of(
                    new InventoryService.Input(a, "50"),
                    new InventoryService.Input(b, "50"),
                    new InventoryService.Input(safe, "50")),
                List.of(new InventoryService.Output("mixed", "150")),
                "0",
                null));
    UUID mixed = id(rows(merge.get("outputs")).getFirst());
    var preview = recalls.preview(n.owner, new RecallService.Preview(List.of(root)));
    assertThat(((Map<?, ?>) preview.get("totals")).get("recoverable")).isEqualTo("150");
    assertThat(((Map<?, ?>) preview.get("totals")).get("unaffected")).isEqualTo("50");
    assertThat(rows(preview.get("affectedLots"))).hasSize(4);
    assertThat(rows(preview.get("exposure"))).hasSize(1);
    n.recall(root);
    assertThatThrownBy(() -> n.draft(mixed, "1", false))
        .extracting("code")
        .isEqualTo("RECALL_HOLD");
    n.draft(safe, "1", false);
  }

  @Test
  void acknowledgment_is_not_recovery_and_closure_requires_accountable_missing_units() {
    var n = new Network();
    UUID lot = n.commission("B", "500"), t = n.ship(lot, "500");
    n.receive(t, lot, "480");
    UUID recall = n.recall(lot);
    recalls.acknowledge(n.partner, recall, key(), new RecallService.Ack("Notice received"));
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "RECOVERED", null))
        .isZero();
    assertThatThrownBy(
            () -> recalls.close(n.owner, recall, key(), new RecallService.Decision("Close")))
        .extracting("code")
        .isEqualTo("CLOSURE_BLOCKED");
    n.recover(n.partner, lot, n.destination, "480");
    assertThatThrownBy(() -> n.recover(n.partner, lot, n.destination, "1"))
        .extracting("code")
        .isEqualTo("INSUFFICIENT_STOCK");
    assertThatThrownBy(
            () -> recalls.close(n.owner, recall, key(), new RecallService.Decision("Close")))
        .extracting("code")
        .isEqualTo("CLOSURE_BLOCKED");
    UUID d = id(rows(custody.detail(n.owner, t).get("discrepancies")).getFirst());
    assertThatThrownBy(
            () ->
                custody.resolve(
                    n.partner,
                    t,
                    key(),
                    new CustodyService.Resolve(
                        d, "ACCOUNTABLE_LOSS", "20", "Carrier count", "Register", null)))
        .isInstanceOf(DomainError.class);
    custody.resolve(
        n.owner,
        t,
        key(),
        new CustodyService.Resolve(
            d,
            "ACCOUNTABLE_LOSS",
            "20",
            "Authorized missing shipment loss",
            "Signed investigation 82",
            null));
    recalls.close(
        n.owner,
        recall,
        key(),
        new RecallService.Decision("480 recovered; 20 accepted loss, partner responded"));
    assertThat(recalls.detail(n.owner, recall).get("recall"))
        .extracting(x -> ((Map<?, ?>) x).get("status"))
        .isEqualTo("CLOSED");
  }

  @Test
  void late_correction_reopens_closed_incident_and_overlapping_holds_remain() {
    var n = new Network();
    UUID lot = n.commission("late", "10"), r1 = n.recall(lot), r2 = n.recall(lot);
    recalls.acknowledge(n.owner, r1, key(), new RecallService.Ack("Owner response"));
    recalls.acknowledge(n.owner, r2, key(), new RecallService.Ack("Owner response"));
    n.recover(n.owner, lot, n.source, "10");
    recalls.close(n.owner, r1, key(), new RecallService.Decision("Recovered"));
    assertThat(ledger.affected(n.owner.workspaceId(), lot)).isTrue();
    recalls.close(n.owner, r2, key(), new RecallService.Decision("Recovered"));
    UUID prior =
        db.uuid(
            db.one(
                    "SELECT event_id FROM inventory_journal WHERE workspace_id=? AND lot_id=? ORDER"
                        + " BY sequence LIMIT 1",
                    n.owner.workspaceId(),
                    lot)
                .get("event_id"));
    long before = db.count("SELECT sequence FROM workspaces WHERE id=?", n.owner.workspaceId());
    inventory.correction(
        n.owner,
        key(),
        new InventoryService.Correction(
            lot,
            n.source,
            prior,
            "ADD",
            "AVAILABLE",
            "2",
            "Late physical count",
            "Count sheet 9",
            Instant.now().minusSeconds(3600).toString()));
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.source, "HELD", null))
        .isEqualByComparingTo("2");
    assertThat(
            db.count(
                "SELECT count(*) FROM recalls WHERE workspace_id=? AND status='ACTIVE'",
                n.owner.workspaceId()))
        .isEqualTo(2);
    assertThat(
            rows(trace.graph(n.owner, before).get("holdings")).stream()
                .noneMatch(h -> "HELD".equals(h.get("status"))))
        .isTrue();
    assertThat(
            db.count(
                "SELECT count(*) FROM recall_revisions WHERE workspace_id=?",
                n.owner.workspaceId()))
        .isEqualTo(6);
  }

  @Test
  void workspace_and_partner_scope_restrict_reads_writes_trace_export() {
    var a = new Network();
    var b = new Network();
    UUID secret = a.commission("private-manufacturer-stock", "20"),
        own = a.commission("received", "10"),
        t = a.ship(own, "10");
    a.receive(t, own, "10");
    assertThat(rows(trace.graph(a.partner, null).get("lots")))
        .extracting(x -> x.get("code"))
        .contains("received")
        .doesNotContain("private-manufacturer-stock");
    assertThatThrownBy(() -> custody.detail(b.owner, t)).isInstanceOf(DomainError.class);
    assertThatThrownBy(
            () ->
                inventory.commission(
                    a.partner,
                    key(),
                    new InventoryService.Commission(
                        a.product, a.source, "forged", "1", LocalDate.now(), null, null)))
        .isInstanceOf(DomainError.class);
    assertThatThrownBy(
            () ->
                inventory.commission(
                    b.owner,
                    key(),
                    new InventoryService.Commission(
                        a.product, a.source, "forged", "1", LocalDate.now(), null, null)))
        .isInstanceOf(DomainError.class);
    var files = unzip(evidence.export(a.partner, null));
    assertThat(new String(files.get("trace.json"), java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain("private-manufacturer-stock");
    assertThat(new String(files.get("journal.json"), java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain(secret.toString());
  }

  Map<String, byte[]> unzip(byte[] bytes) {
    try (var input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      Map<String, byte[]> files = new LinkedHashMap<>();
      ZipEntry e;
      while ((e = input.getNextEntry()) != null) files.put(e.getName(), input.readAllBytes());
      return files;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void complete_snapshot_manifest_detects_changed_bytes_and_historical_values() {
    var n = new Network();
    UUID lot = n.commission("history", "10");
    long seq = db.count("SELECT sequence FROM workspaces WHERE id=?", n.owner.workspaceId());
    UUID transfer = n.ship(lot, "4");
    var old = rows(trace.graph(n.owner, seq).get("holdings"));
    assertThat(old)
        .anyMatch(h -> "AVAILABLE".equals(h.get("status")) && "10".equals(h.get("quantity")));
    var files = unzip(evidence.export(n.owner, null));
    var manifest =
        json.read(new String(files.get("manifest.json"), java.nio.charset.StandardCharsets.UTF_8));
    var declared = (Map<?, ?>) manifest.get("files");
    declared.forEach(
        (name, value) ->
            assertThat(Json.hash(files.get(name.toString())))
                .isEqualTo(((Map<?, ?>) value).get("sha256")));
    byte[] modified = files.get("journal.json").clone();
    modified[1] ^= 1;
    assertThat(Json.hash(modified))
        .isNotEqualTo(((Map<?, ?>) declared.get("journal.json")).get("sha256"));
  }

  @Test
  void outbox_lease_crash_reclaim_and_duplicate_delivery_are_restart_safe() {
    var n = new Network();
    UUID lot = n.commission("notify", "10");
    n.ship(lot, "5");
    var claimed = worker.claim();
    var entry =
        claimed.stream()
            .filter(x -> n.owner.workspaceId().toString().equals(x.get("workspace_id")))
            .findFirst()
            .orElseThrow();
    UUID w = n.owner.workspaceId(), id = db.uuid(entry.get("id"));
    assertThat(worker.claim()).noneMatch(x -> id.toString().equals(x.get("id")));
    db.update(
        "UPDATE outbox SET lease_until=now()-interval '1 second' WHERE workspace_id=? AND id=?",
        w,
        id);
    assertThat(worker.claim()).anyMatch(x -> id.toString().equals(x.get("id")));
    worker.deliver(w, id);
    worker.deliver(w, id);
    assertThat(
            db.count(
                "SELECT count(*) FROM inbox WHERE workspace_id=? AND event_id=? AND recipient_id=?",
                w,
                db.uuid(entry.get("event_id")),
                db.uuid(entry.get("recipient_id"))))
        .isEqualTo(1);
    assertThat(
            db.one("SELECT delivered_at FROM outbox WHERE workspace_id=? AND id=?", w, id)
                .get("delivered_at"))
        .isNotNull();
  }

  @Test
  void rotation_replay_revokes_session_logout_and_http_origin_body_isolation() throws Exception {
    var n = new Network();
    var refreshed = auth.refresh(n.issued.refresh());
    assertThat(refreshed.refresh()).isNotEqualTo(n.issued.refresh());
    assertThatThrownBy(() -> auth.refresh(n.issued.refresh()))
        .extracting("code")
        .isEqualTo("SESSION_REPLAY");
    assertThatThrownBy(() -> auth.authenticate(refreshed.body().accessToken()))
        .isInstanceOf(DomainError.class);
    var login = auth.login(new IdentityService.Login(n.email, PASSWORD));
    var client = HttpClient.newHttpClient();
    var bad =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/refresh"))
            .header("Origin", "https://evil.example")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    assertThat(client.send(bad, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    var forged =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/trace"))
            .header("Authorization", "Bearer " + login.body().accessToken())
            .header("X-Workspace-Id", UUID.randomUUID().toString())
            .GET()
            .build();
    assertThat(client.send(forged, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(403);
    var unknown =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
            .header("Origin", "http://localhost:5173")
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key())
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"forged@example.test\",\"password\":\"Test-account-password-2026\",\"name\":\"Forged\",\"workspaceName\":\"X\",\"organizationName\":\"Y\",\"role\":\"OWNER\"}"))
            .build();
    assertThat(client.send(unknown, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(422);
    auth.logout(login.refresh());
    assertThatThrownBy(() -> auth.authenticate(login.body().accessToken()))
        .isInstanceOf(DomainError.class);
  }

  @Test
  void http_invitation_optional_grants_and_decimal_string_validation() throws Exception {
    var n = new Network();
    var client = HttpClient.newHttpClient();
    UUID org =
        db.uuid(
            db.one(
                    "SELECT organization_id FROM sites WHERE workspace_id=? AND id=?",
                    n.owner.workspaceId(),
                    n.destination)
                .get("organization_id"));
    String request =
        json.write(
            Map.of(
                "organizationId",
                org.toString(),
                "email",
                key() + "@example.test",
                "role",
                "PARTNER_OPERATOR",
                "recallManager",
                false,
                "siteIds",
                List.of(n.destination.toString())));
    var invite =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/workspace/invitations"))
            .header("Authorization", "Bearer " + n.issued.body().accessToken())
            .header("Origin", "http://localhost:5173")
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key())
            .POST(HttpRequest.BodyPublishers.ofString(request))
            .build();
    var result = client.send(invite, HttpResponse.BodyHandlers.ofString());
    assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
    String numeric =
        json.write(
            Map.of(
                "productId",
                n.product.toString(),
                "siteId",
                n.source.toString(),
                "code",
                "numeric-forged",
                "quantity",
                1.5,
                "issuedOn",
                "2026-10-01"));
    var command =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/inventory/commission"))
            .header("Authorization", "Bearer " + n.issued.body().accessToken())
            .header("Origin", "http://localhost:5173")
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key())
            .POST(HttpRequest.BodyPublishers.ofString(numeric))
            .build();
    assertThat(client.send(command, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(422);
  }

  @Test
  void scoped_sender_sees_outstanding_claims_and_receipt_event_without_receiver_inventory() {
    var n = new Network();
    String email = key() + "@example.test";
    var invitation =
        workspace.invite(
            n.owner,
            key(),
            new WorkspaceService.Invite(
                n.owner.organizationId(), email, "OPERATOR", false, List.of(n.source)));
    var operator =
        auth.authenticate(
            auth.accept(
                    new IdentityService.Accept(
                        invitation.get("invitationPath").toString().split("token=")[1],
                        email,
                        PASSWORD,
                        "Packing operator"))
                .body()
                .accessToken());
    UUID lot = n.commission("outbound", "500"), t = n.ship(lot, "500");
    n.receive(t, lot, "480");
    var graph = trace.graph(operator, null);
    assertThat(rows(graph.get("holdings")))
        .anyMatch(h -> "UNRESOLVED".equals(h.get("status")) && "20".equals(h.get("quantity")));
    assertThat(rows(graph.get("holdings")))
        .noneMatch(h -> "AVAILABLE".equals(h.get("status")) && "480".equals(h.get("quantity")));
    assertThat(rows(graph.get("events")))
        .anyMatch(e -> e.get("type").toString().startsWith("TRANSFER_RECEIVED:"));
    UUID second = n.commission("draft-cancellation", "10");
    var draft = n.draft(second, "1", false);
    UUID cancelled = id(draft);
    long before = ((Number) draft.get("sequence")).longValue();
    custody.cancel(n.owner, cancelled, key(), new CustodyService.Cancel("Draft withdrawn", 0));
    assertThat(trace.transfers(operator, before))
        .anyMatch(x -> cancelled.toString().equals(x.get("id")) && "DRAFT".equals(x.get("status")));
    assertThat(trace.transfers(operator, trace.sequence(operator, null)))
        .anyMatch(
            x -> cancelled.toString().equals(x.get("id")) && "CANCELLED".equals(x.get("status")));
  }

  @Test
  void site_grants_filter_graph_transfers_timeline_export_and_notifications() {
    var n = new Network();
    UUID secondSite =
        id(
            workspace.site(
                n.owner,
                key(),
                new WorkspaceService.Site(
                    n.partner.organizationId(), "Private second receiving site")));
    String email = key() + "@example.test";
    var invitation =
        workspace.invite(
            n.owner,
            key(),
            new WorkspaceService.Invite(
                n.partner.organizationId(), email, "PARTNER_OPERATOR", false, List.of(secondSite)));
    var second =
        auth.authenticate(
            auth.accept(
                    new IdentityService.Accept(
                        invitation.get("invitationPath").toString().split("token=")[1],
                        email,
                        PASSWORD,
                        "Second-site operator"))
                .body()
                .accessToken());
    UUID lot = n.commission("private-second-site-lot", "273");
    UUID transfer =
        id(
            custody.draft(
                n.owner,
                key(),
                new CustodyService.Draft(
                    n.source,
                    secondSite,
                    "Private second-site shipment",
                    List.of(new CustodyService.Line(lot, "273")),
                    false,
                    null)));
    custody.dispatch(n.owner, transfer, key(), new CustodyService.Dispatch(0, null));
    custody.receive(
        second,
        transfer,
        key(),
        new CustodyService.Receipt(
            1, List.of(new CustodyService.Received(lot, "273", "GOOD", "")), null));
    var graph = trace.graph(n.partner, null);
    assertThat(rows(graph.get("lots"))).noneMatch(x -> lot.toString().equals(x.get("id")));
    assertThat(rows(graph.get("transfers")))
        .noneMatch(x -> transfer.toString().equals(x.get("id")));
    assertThat(rows(graph.get("events"))).noneMatch(x -> json.write(x).contains("273"));
    assertThat(custody.list(n.partner)).noneMatch(x -> transfer.toString().equals(x.get("id")));
    assertThatThrownBy(() -> custody.detail(n.partner, transfer)).isInstanceOf(DomainError.class);
    for (var out : worker.claim())
      worker.deliver(db.uuid(out.get("workspace_id")), db.uuid(out.get("id")));
    assertThat(
            db.rows(
                "SELECT message FROM inbox WHERE workspace_id=? AND recipient_id=?",
                n.owner.workspaceId(),
                n.partner.userId()))
        .noneMatch(x -> x.get("message").toString().contains("Private second-site"));
    var packet = unzip(evidence.export(n.partner, null));
    assertThat(new String(packet.get("events.json"), java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain(lot.toString());
  }

  @Test
  void explicit_network_auditor_reads_but_cannot_write_and_permission_revocation_is_rechecked() {
    var n = new Network();
    UUID lot = n.commission("network-audit-lot", "10");
    String email = key() + "@example.test";
    var invite =
        workspace.invite(
            n.owner,
            key(),
            new WorkspaceService.Invite(
                n.partner.organizationId(), email, "AUDITOR", false, List.of(), true));
    var auditor =
        auth.authenticate(
            auth.accept(
                    new IdentityService.Accept(
                        invite.get("invitationPath").toString().split("token=")[1],
                        email,
                        PASSWORD,
                        "Network auditor"))
                .body()
                .accessToken());
    assertThat(rows(trace.graph(auditor, null).get("lots")))
        .anyMatch(x -> lot.toString().equals(x.get("id")));
    assertThatCode(() -> n.draft(lot, "1", false)).doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                inventory.disposition(
                    auditor,
                    key(),
                    new InventoryService.Disposition(
                        lot, n.source, "HOLD", "AVAILABLE", "1", "Audit attempt", "Ref", null)))
        .isInstanceOf(DomainError.class);
    workspace.permissions(
        n.owner,
        key(),
        new WorkspaceService.Permissions(
            n.partner.membershipId(), "AUDITOR", false, List.of(n.destination)));
    assertThatThrownBy(
            () ->
                custody.draft(
                    n.partner,
                    key(),
                    new CustodyService.Draft(
                        n.destination,
                        n.source,
                        "Stale grant",
                        List.of(new CustodyService.Line(lot, "1")),
                        false,
                        null)))
        .extracting("code")
        .isEqualTo("MEMBERSHIP_CHANGED");
  }

  @Test
  void unknown_custody_is_an_observation_gap_not_zero_or_extra_stock() {
    var n = new Network();
    UUID lot = n.commission("unknown", "10");
    UUID gap =
        id(
            observations.report(
                n.owner,
                key(),
                new ObservationService.Observation(
                    lot,
                    "4",
                    "Partner reports untracked downstream stock",
                    "Investigation ref 21",
                    null)));
    var preview = recalls.preview(n.owner, new RecallService.Preview(List.of(lot)));
    assertThat(rows(preview.get("gaps")))
        .anyMatch(x -> "UNKNOWN_CUSTODY_OBSERVATION".equals(x.get("kind")));
    assertThat(((Map<?, ?>) preview.get("totals")).get("recoverable")).isEqualTo("10");
    UUID recall = n.recall(lot);
    recalls.acknowledge(n.owner, recall, key(), new RecallService.Ack("Responded"));
    n.recover(n.owner, lot, n.source, "10");
    assertThatThrownBy(
            () ->
                recalls.close(
                    n.owner,
                    recall,
                    key(),
                    new RecallService.Decision("Missing evidence still open")))
        .extracting("code")
        .isEqualTo("CLOSURE_BLOCKED");
    observations.resolve(
        n.owner,
        gap,
        key(),
        new ObservationService.Resolution(
            "Count was a duplicate of the recovered stock; independently reviewed partner register",
            "Signed reconciliation 31"));
    recalls.close(
        n.owner, recall, key(), new RecallService.Decision("Recovery and evidence gap reconciled"));
  }

  @Test
  void custody_returns_do_not_make_ancestry_and_dispatched_cancellation_keeps_unresolved_stock() {
    var n = new Network();
    UUID lot = n.commission("return", "10"), t = n.ship(lot, "10");
    n.receive(t, lot, "10");
    UUID back =
        id(
            custody.draft(
                n.partner,
                key(),
                new CustodyService.Draft(
                    n.destination,
                    n.source,
                    "Return movement",
                    List.of(new CustodyService.Line(lot, "10")),
                    false,
                    null)));
    custody.dispatch(n.partner, back, key(), new CustodyService.Dispatch(0, null));
    custody.receive(
        n.owner,
        back,
        key(),
        new CustodyService.Receipt(
            1, List.of(new CustodyService.Received(lot, "10", "GOOD", "Returned count")), null));
    assertThat(db.count("SELECT count(*) FROM lineage WHERE workspace_id=?", n.owner.workspaceId()))
        .isZero();
    UUID next = n.ship(lot, "10");
    custody.cancel(
        n.owner,
        next,
        key(),
        new CustodyService.Cancel("Shipment stopped; custody requires reconciliation", 1));
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.source, "AVAILABLE", null)).isZero();
    assertThat(ledger.quantity(n.owner.workspaceId(), lot, n.destination, "UNRESOLVED", next))
        .isEqualByComparingTo("10");
    assertThat(
            db.count(
                "SELECT count(*) FROM receipts WHERE workspace_id=? AND transfer_id=?",
                n.owner.workspaceId(),
                next))
        .isZero();
  }
}
