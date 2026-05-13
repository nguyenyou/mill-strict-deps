# Changelog

## 1.3.1 - 2026-05-13

### Changed

- Renamed the compile-order task from `strictDepsCompileWaves` to `strictDepsCompileDepth`.
- Renamed compile-order report terminology from "wave" to "depth" to match the longest upstream direct-dependency path calculation.
- Updated README examples and tests to use the new `strictDepsCompileDepth` task.

### Notes

- `compile depth 0` means a dependency module has no upstream module deps inside the selected dependency graph.
- `compile depth N` means the module's longest upstream direct-dependency path has `N` edges.

