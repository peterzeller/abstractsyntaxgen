# Generator contracts for incremental compiler artifacts

Complete these generator-owned features before widening Wurst backend caching. Keep existing unannotated schemas compatible; no generated Java is edited by hand.

1. Owned metadata collections: tracked List/Set/Map operations and nested views, defensive container copies, alias preservation, detached-view lifetime, fail-before-write checks. Cover constructors, persistent fields and copy variants. Explicitly diagnose structural container cycles; AST reference cycles belong to snapshot support.
2. Structured reference metadata: schema-visible reference roles inside containers and supported sum values; generated traversal/repair/codec coverage without a compiler-maintained field list. Unknown external value semantics use a documented adapter contract, not silent guesses.
3. Semantic versus source revisions: schema field roles, ownership-edge propagation, metadata/list mutations, copy/reset behavior, no propagation through reference targets.
4. Immutable snapshots and ownership validation: generated private allocation/population, iterative deep-tree handling, internal cycles and external symbolic references, strict detached values, round trips and mutation scopes that reject foreign writes before changing data.

Exit gates: adversarial regression tests and planted mutants, all generator tests on Java25/27, deterministic output, all real Wurst schemas generate and compile, and independent Wurst fresh-build oracles. Preserve emitted-program semantics; compiler dependency facts and compile-time execution remain compiler-owned. Do not claim a hot-reload latency improvement until a backend consumer proves bounded work on real edits.

Implementation status: all four generator contracts are implemented on this branch. Validation includes
schema-role determinism, deep graph/container round trips, reference-list and nested-reference repair,
source-domain aliasing, failed attachment atomicity and foreign-write guards. All four real Wurst schemas generate and compile, and 96 focused scalar-backend, retained/fresh
compilation and determinism tests pass against the local generator. Four planted faults (source-domain
notification, nested-reference remapping, foreign-write checks and detached-view lifetime) each fail
their behavioral regression; the restored implementation passes. Current-head CI/review clearance
and the final real-map replay remain exit gates. Backend dependency meaning, reachability,
compile-time effects and specialization identities remain compiler responsibilities.
