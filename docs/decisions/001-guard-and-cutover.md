# Workspace guard and separate schema

Accepted 2026-10-12.

Use one PostgreSQL workspace guard row for all stock, genealogy and recall mutations. A shared lock is necessary for activation to be atomic against dispatch / consumption / reservations. Serializing writes is an explicit throughput tradeoff for this first release; reads use consistent snapshots without taking the write guard. Revisit it only with equivalent PostgreSQL concurrency evidence.

Use a new `lotline` schema/history without changing legacy `public` facts or Flyway checksums. The old physical-image model cannot establish batch genealogy. Keep data, remove obsolete runnable application paths, and require explicit reviewed import rather than deriving nonexistent custody.

Use JDBC services and API records instead of JPA persistence entities. Exact SQL, workspace-qualified keys and append-only journal transactions are the authority. PostgreSQL Testcontainers, rather than H2 or a fake inventory store, verifies these decisions.
