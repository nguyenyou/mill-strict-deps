# Changelog

## 1.5.0 - 2026-05-13

### Added

- Added Zinc classpath reachability summary rows to `strictDepsWeight`.
- Added Zinc classpath reachability summary rows to `strictDepsCompileDepth`.

### Changed

- `used classes` cells now show `zero` when no dependency classes are directly used.

### Notes

- Reachability starts from dependency classes directly used by the current module, then follows Zinc class dependencies through transitive compile module deps.
- The reachability summary is compile-analysis reachability, not runtime main-method reachability from a linker.

## 1.4.2 - 2026-05-13

### Added

- Added `own lines` and `own classes` columns to `strictDepsCommonAncestors`.
- Added a `used classes` column to `strictDepsWeight`.
- Added a `used classes` column to `strictDepsCompileDepth`.

### Notes

- `used classes` is shown as `used / total (percent)`.
- Used class counts are based on classes from a dependency module directly referenced by the current module according to Zinc analysis.
- `strictDepsCommonAncestors` now reads compile analysis for graph snapshot nodes so class counts are available in whole-build ancestry output.

## 1.4.1 - 2026-05-13

### Added

- Added `strictDepsCommonAncestors` for whole-build common-ancestor analysis across collected strict-deps graph snapshots.
- Added source-line metrics to `strictDepsWeight` and `strictDepsCompileDepth`: `own lines`, `absolute lines`, and `delta lines`.
- Added Zinc class-count metrics to dependency weight reports: current, dependency, and total classes in the summary, plus `own classes` and `absolute classes` per dependency.
- Added the new source-line and class-count metrics to Markdown and JSON reports.

### Notes

- Source-file and source-line metrics are calculated from both Mill planned compiler input and Zinc compile analysis, then compared.
- Class-count metrics are Zinc-only because Mill source discovery does not know which classes the compiler produced.
- Dependency snapshots now carry source file inputs so line counting stays visible to Mill task input tracking.

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
