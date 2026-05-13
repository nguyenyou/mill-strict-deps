# Changelog

## 1.4.0 - 2026-05-13

### Added

- Added a `delta weight` column to `strictDepsWeight`.
- Added a `delta weight` column to `strictDepsCompileDepth`.
- Added README badges for Scala 3 and Maven Central.

### Notes

- In `strictDepsWeight`, delta weight is the number of source files first introduced by that row in the absolute-weight sorted list.
- In `strictDepsCompileDepth`, delta weight is the number of source files first introduced by that row in top-down compile-depth order.
- Delta weights help explain which modules add unique compile input files instead of only showing overlapping absolute weights.

## 1.3.1 - 2026-05-13

### Changed

- Renamed the compile-order task from `strictDepsCompileWaves` to `strictDepsCompileDepth`.
- Renamed compile-order report terminology from "wave" to "depth" to match the longest upstream direct-dependency path calculation.
- Updated README examples and tests to use the new `strictDepsCompileDepth` task.

### Notes

- `compile depth 0` means a dependency module has no upstream module deps inside the selected dependency graph.
- `compile depth N` means the module's longest upstream direct-dependency path has `N` edges.
