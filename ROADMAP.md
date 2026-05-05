# Roadmap

## Phase 1

- Report unused direct Mill module dependencies.
- Report missing direct Mill module dependencies.
- Keep output markdown and human-readable.

## Phase 2

- Add suppression config with explicit reasons.
- Add JSON output for tooling.
- Add CI baseline mode.

## Phase 3

- Map used library jars back to direct Maven deps.
- Report unused direct `mvnDeps`.
- Report indirect Maven deps used without direct declaration.

## Phase 4

- Harden edge cases:
  - resources
  - reflection
  - ServiceLoader
  - macros
  - annotation processors
  - compiler plugins

