# Tenant isolation

SupplyPrint creates a tenant for each self-service registration. The JWT includes `tenant_id`; `TenantContext` is set only after successful JWT validation. Core records (`users`, `product_fingerprints`, `verification_events`, and `blockchain_outbox`) carry a non-null tenant ID.

Core enrollment, verification, evidence lookup, dashboard SQL, audit events, and on-chain metadata updates are tenant-scoped. Product IDs are unique within a tenant, not globally. The migration adds a legacy tenant for pre-existing rows.

Current invitation/organization-membership workflows are intentionally not implemented; an enterprise deployment should add tenant-admin provisioning, audited membership changes, and optionally PostgreSQL row-level security as a defense-in-depth layer.
