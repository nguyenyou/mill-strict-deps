# Changelog

## 1.12.1 - 2026-08-07

### Fixed

- Fixed strict-deps analysis when a cached Mill compilation result contains
  class outputs but no readable Zinc analysis. The plugin now regenerates Zinc
  analysis with the module's compiler inputs and materializes it as a
  cache-tracked `PathRef` before analysis.
- Fixed `strictDepsApplyFix` source-file resolution for Mill 1.2 daemon builds
  whose build files are exposed through the `../mill-workspace` alias.

### Changed

- Updated the plugin build from Mill `1.1.6-7-15943a` to
  `1.2.0-RC1-46-16168f` and Zinc from `2.0.0-M17` to `2.0.1`.

### Notes

- Existing Zinc analysis is reused when valid. Regeneration runs only when the
  analysis is missing or unreadable; the plugin does not fall back to parsing
  class files.

## 1.12.0 - 2026-05-15

### Changed

- Changed `strictDepsCompileDepth` to print only the depth table and target row
  by default. Pass `--showSummary true` to include the summary block.

### Added

- Added total target-row values for directly referenced classes, reachable
  classes, and reachable sources in `strictDepsCompileDepth`.

## 1.11.0 - 2026-05-15

### Added

- Added `strictDepsCompileDepth --zeroReachableSourcesOnly true` to keep only
  compile-world dependency rows whose reachable source count is zero.

## 1.10.0 - 2026-05-15

### Added

- Added global `strictDepsDownstreamUsage --target <module>` command. It
  collects `__.strictDepsCompileWasteSnapshot`, filters to one dependency
  module, and prints each downstream client with directly referenced classes,
  reachable classes, and reachable sources for that dependency.
- Rendered `strictDepsDownstreamUsage` rows with the same colored relationship
  marker and count/progress bars used by `strictDepsCompileDepth`.
- Added an unlabeled row-number column to `strictDepsDownstreamUsage` so sorted
  downstream rows are numbered in the printed table.
- Added direct-reference class counts to compile-waste snapshot dependency data
  so whole-build reverse-usage views can reuse the same per-client facts as
  `strictDepsCompileDepth`.

### Notes

- This is the inverse of running `strictDepsCompileDepth` on many clients. Start
  with a module such as `uiWidget`, then compare how much each selected
  client actually reaches from it.

## 1.9.1 - 2026-05-15

### Fixed

- Fixed `strictDepsApplyFix` so documented inherited dependency shapes such as
  `super.moduleDeps ++ Seq(...)` and `Seq(...) ++ super.moduleDeps` are parsed
  safely instead of being refused.
- Fixed autofix add/remove matching for build files that use simple wildcard
  imports such as `import build.<prefix>.*` or `import build.<prefix>._`, so
  edits preserve the existing imported source style when safe.

### Notes

- Dynamic dependency expressions, cross modules, and ambiguous removals are
  still refused. The autofix remains conservative and only edits explicit
  supported `Seq(...)`, `Seq.empty`, and `Nil` dependency expressions.

## 1.9.0 - 2026-05-15

### Added

- Added `strictDepsAutofixPlan`, which writes an apply-ready Markdown plan for
  adding missing direct deps and removing unused direct deps without mutating
  `build.mill`.
- Added `strictDepsApplyFix(dryRun)`, an all-or-nothing command that previews or
  applies safe `moduleDeps` / `compileModuleDeps` edits only when every change
  maps to an exact supported source edit.
- Added a conservative autofix planner and renderer for supported dependency
  shapes: `Seq(...)`, `Seq.empty`, `Nil`, `super.moduleDeps ++ Seq(...)`, and
  `Seq(...) ++ super.moduleDeps`.
- Added autofix tests covering simple add/remove edits, multiline sequences,
  inserted dependency methods, compile-only additions, dynamic-shape refusals,
  and cross-module refusal cases.

### Changed

- Renamed the human-facing `used classes` metric to `directly referenced classes`
  and `Dependency Usage Weight` to `Dependency Reference Weight` in reports and
  terminal tables, to make it clear that this is Zinc's direct source-reference
  count and not classpath reachability.
- Documented the autofix workflow in the README and bundled agent skill docs.

### Notes

- Autofix currently supports explicit `Seq(...)`, `Seq.empty`, `Nil`, and
  `super.<deps> ++ Seq(...)` dependency definitions. It refuses dynamic source
  shapes, cross modules, ambiguous removals, and any plan that would require a
  guess.
- The JSON schema remains version `4`; this release changes human-facing labels
  but keeps existing JSON field names stable.

## 1.8.1 - 2026-05-15

### Fixed

- Fixed classpath reachability so classes defined from the same source file are
  treated as peers during the reachability walk. This prevents
  companion/object-created helper classes from being incorrectly reported as
  unused.

### Changed

- Updated the bundled agent skill docs with the `strictDepsWhoIntroduces --target`
  workflow.

### Notes

- Added a regression fixture for a facade object that constructs a backend helper
  and calls a child wrapper, while keeping an intentionally unused component as
  the negative control.

## 1.8.0 - 2026-05-15

### Added

- Added `strictDepsWhoIntroduces(target)` command. Given a module name on the command line, prints the shortest transitive chain from each direct compile module dep of the current module to that target. Useful for tracing how an unwanted or unused transitive dep gets pulled in.

### Notes

- Implemented as BFS over the same module dependency graph used by `dependencyModuleClosure`, so the result stays consistent with the rest of the analyzer.
- One row per introducing direct dep. Direct deps whose closure does not reach the target are silently omitted. If no direct dep introduces it, the command prints a one-liner instead of an empty table.

## 1.7.0 - 2026-05-15

### Added

- Added color progress bars to `used classes`, `reachable classes`, and `reachable sources` cells in `strictDepsCompileDepth`. Bar color interpolates red (0%) through yellow (50%) to green (100%) via the `fansi` library.
- Added a colored block prefix to the `relationship` column: green block for `direct`, blue block for `transitive`.

### Changed

- Renamed `strictDepsCompileDepth` headers `own weight` / `absolute weight` / `delta weight` to `own sources` / `absolute sources` / `delta sources`. Mill/Zinc divergence note labels follow the rename.
- Zero-coverage cells now show `0 / N` with an all-red bar instead of the literal text `zero`, matching the format of non-zero rows.

### Removed

- Removed `absolute lines` and `delta lines` columns from `strictDepsCompileDepth`. The `own lines` column stays.

### Notes

- The gradient uses 24-bit ANSI (`fansi.Color.True(r, g, b)`). Modern terminals render it natively; older or non-color terminals will see the escape codes but the underlying counts remain readable.
- Column widths stay correctly aligned because the renderer pads on visible-width (`fansi.Str.length`) and only renders ANSI bytes at the very end.

## 1.6.0 - 2026-05-13

### Added

- Added `strictDepsCompileWaste` for module-level compile-waste analysis.
- Added global `strictDepsCompileWaste` aggregation over collected
  `__.strictDepsCompileWasteSnapshot` tasks.
- Added compile-waste data to `strictDepsJsonReport`.
- Added reachable and wasted delta-source metrics to dependency weight data.
- Added `introducedByModuleNames` so reports can show which direct edge pulled
  in each dependency row.
- Added reachable class and reachable source columns to `strictDepsWeight` and
  `strictDepsCompileDepth`.

### Changed

- Bumped the JSON report schema version to `4`.
- Expanded `strictDepsJsonReport` with the full weight report and compile-waste
  snapshot behind the Markdown and command-line views.
- Documented the compile-waste metrics, module command, global command, and new
  JSON fields in the README.

### Notes

- Compile waste compares dependency source files made available to a client with
  the source files reachable from the dependency classes the client actually
  uses, then ranks high-waste nodes and edges first.
- The global command is most useful for finding direct module edges that make
  many clients compile source files they do not reach through Zinc analysis.

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
