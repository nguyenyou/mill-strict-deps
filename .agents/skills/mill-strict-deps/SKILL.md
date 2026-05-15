---
name: mill-strict-deps
description: "Use when working with the mill-strict-deps Mill plugin in Scala/JVM or Java/JVM Mill builds: installing the plugin, mixing `StrictDepsModule` into modules, running strict dependency reports, interpreting unused direct deps, missing direct deps, dependency weight, compile depth, compile waste, classpath reachability, who-introduces transitive dependency chains, JSON output, fix plans, autofix plans, checks, graph snapshots, and whole-build global commands."
---

# Mill Strict Deps

## Mental Model

Use this skill to get the most signal from `mill-strict-deps`.

The plugin asks one simple question:

```text
declared direct internal deps
        |
        v
classes Zinc says this module referenced
        |
        v
unused direct deps + missing direct deps + compile-cost clues
```

It compares Mill `moduleDeps` and `compileModuleDeps` against Zinc compile analysis. It focuses on internal JVM module dependencies, not external Maven dependency cleanup.

## Setup

Add the plugin to the `build.mill` header, using the version the user wants:

```scala
//| mvnDeps:
//| - io.github.nguyenyou::mill-strict-deps::<version>
```

Mix the trait into every JVM module that should expose strict-deps tasks:

```scala
import io.github.nguyenyou.millstrictdeps.StrictDepsModule

object appA extends ScalaModule with StrictDepsModule {
  def scalaVersion = "3.8.3"
  def moduleDeps = Seq(appB)
}
```

The plugin supports Scala/JVM and Java/JVM sources inside Mill `ScalaModule`s. It reads `moduleDeps`, `compileModuleDeps`, `allSourceFiles`, and the Zinc analysis file produced by `compile`.

## Pick The Command

Use `strictDepsJsonReport` first when automation or exact facts matter. Use human-readable commands when deciding what to fix.

```text
What is wrong with this one module?
  ./mill appA.strictDepsReport

Need exact machine-readable facts?
  ./mill appA.strictDepsJsonReport

Want suggested edits, but no mutation?
  ./mill appA.strictDepsFixPlan

Want an apply-ready safe edit plan?
  ./mill appA.strictDepsAutofixPlan

Want to preview the exact automatic edit?
  ./mill appA.strictDepsApplyFix --dryRun true

Want to apply safe direct-dep edits to build.mill?
  ./mill appA.strictDepsApplyFix

Want CI to fail on dependency shape?
  ./mill appA.strictDepsCheck

Which deps carry the most source/class weight?
  ./mill appA.strictDepsWeight

What is the compile-order shape?
  ./mill appA.strictDepsCompileDepth

Which edge made this module compile unreachable sources?
  ./mill appA.strictDepsCompileWaste

Which direct dep pulls in an unwanted transitive module?
  ./mill appA.strictDepsWhoIntroduces --target uiWidget

Which modules are upstream of most of the build?
  ./mill io.github.nguyenyou.millstrictdeps.strictDepsCommonAncestors/

Which nodes and edges waste compile input across the build?
  ./mill io.github.nguyenyou.millstrictdeps.strictDepsCompileWaste/

Which downstream clients need one upstream module?
  ./mill io.github.nguyenyou.millstrictdeps.strictDepsDownstreamUsage/ --target uiWidget
```

When chaining Mill tasks, separate top-level tasks with `+`:

```text
./mill appA.strictDepsJsonReport + appA.strictDepsFixPlan
```

## Module Tasks

`strictDepsReport`
: Write `out/<module>/strictDepsReport.dest/strict-deps-report.md`. Contains summary, classpath reachability, dependency source weight, dependency reference weight, unused direct deps, missing direct deps, used direct deps, and used library classpath entries.

`strictDepsJsonReport`
: Write `out/<module>/strictDepsJsonReport.dest/strict-deps-report.json`. This is the richest output. Schema version `4` includes strict-deps facts, `reachability`, `weightReport`, and `compileWaste`.

`strictDepsFixPlan`
: Write `out/<module>/strictDepsFixPlan.dest/strict-deps-fix-plan.md`. Suggests direct deps to add and remove. It never edits `build.mill`.

`strictDepsAutofixPlan`
: Write `out/<module>/strictDepsAutofixPlan.dest/strict-deps-autofix-plan.md`. Builds an apply-ready plan for adding missing direct deps and removing unused direct deps from the module's source file, but does not mutate files.

`strictDepsApplyFix --dryRun true`
: Print the same autofix plan as a command dry run. No files are changed.

`strictDepsApplyFix`
: Apply the autofix plan to the module's source file. It edits only supported `moduleDeps` / `compileModuleDeps` shapes such as `Seq(...)`, `Seq.empty`, `Nil`, `super.moduleDeps ++ Seq(...)`, and `Seq(...) ++ super.moduleDeps`. It refuses the whole apply when any planned add/remove is unsafe, including dynamic dependency expressions, cross module expressions that cannot be synthesized safely, or removals whose exact expression cannot be matched.

`strictDepsCheck`
: Fail when unused direct deps or missing direct deps exist, subject to the module settings below. Use for CI once false positives are understood.

`strictDepsWeight`
: Print a terminal table sorted by absolute dependency source weight. Use it to find broad direct or transitive deps and see Mill/Zinc source counts, source lines, classes, directly referenced classes, reachable classes, reachable sources, and delta weight.

`strictDepsCompileDepth`
: Print the same weight data grouped by compile depth. Use it to read the dependency graph from upstream modules down to the target module.

`strictDepsCompileWaste`
: Print a waste-first table for one module. Use it to answer: "which dependency row introduced source files this module cannot reach through Zinc class dependencies?" It accepts `limit`, default `50`.

`strictDepsWhoIntroduces --target <target>`
: Print the shortest transitive module-dependency chain from each direct compile module dep of the current module to `target`. Use it when a module appears in weight, depth, or waste output and you need to know which direct dep is pulling it in. Direct deps whose closure does not reach `target` are omitted. If none reaches `target`, the command prints a one-line message.

`strictDepsGraphSnapshot`
: Internal snapshot task used by global `strictDepsCommonAncestors`. It records graph nodes, direct edges, own source count, own line count, and own class count.

`strictDepsCompileWasteSnapshot`
: Internal snapshot task used by global `strictDepsCompileWaste`. It records per-client compile-waste facts.

## Global Commands

Run these from the external module names:

```text
./mill io.github.nguyenyou.millstrictdeps.strictDepsCommonAncestors/
./mill io.github.nguyenyou.millstrictdeps.strictDepsCompileWaste/
./mill io.github.nguyenyou.millstrictdeps.strictDepsDownstreamUsage/ --target uiWidget
```

`strictDepsCommonAncestors` collects `__.strictDepsGraphSnapshot`. It ranks modules by how many analyzed modules eventually depend on them. A row where `needed by == comparable` is a common ancestor.

`strictDepsCompileWaste` collects `__.strictDepsCompileWasteSnapshot`. It prints:

- `bad nodes`: dependency modules with repeated wasted delta source count.
- `bad edges`: client -> dependency rows with high wasted delta source count.

`strictDepsDownstreamUsage --target <target>` collects `__.strictDepsCompileWasteSnapshot`, filters to one dependency module, and prints each selected downstream client that has it in the compile world. Rows include an unlabeled row-number column after sorting. The key columns are directly referenced classes, reachable classes, and reachable sources for the target dependency in that client, rendered with the same colored progress bars as `strictDepsCompileDepth`. To limit the universe to selected clients, pass one Mill selector expression positionally, for example `'{clientA.js,clientB.js}.strictDepsCompileWasteSnapshot'`.

All global commands accept `limit`, default `50`.

## Interpret The Facts

Think in boxes:

```text
appA source mentions uiWidget.Button

declared:
  appA -> appB -> uiWidget

strict shape:
  appA -> uiWidget
```

`used direct module deps`
: Direct internal deps whose classes Zinc saw the current module reference.

`unused direct module deps`
: Direct internal deps where Zinc recorded no compile-time class use. Often removable, but watch for resources, reflection, generated code, macros, annotation processors, framework conventions, or runtime-only edges.

`missing direct module deps`
: Transitive internal modules whose classes were directly referenced by current source. Usually add these as direct deps.

`dependency reference weight`
: Advisory class coupling: directly referenced classes from dependency, share of this module's internal dependency references, and share of that dependency's class surface referenced.

`dependency source weight`
: Compile-cost signal. `own` is the dependency module itself. `absolute` is that module plus reachable transitive module deps. `delta` is the unique source count contributed by that row in the current ordering.

`Mill / Zinc` count differences
: Mill counts planned compiler source inputs. Zinc counts sources recorded in compile analysis. Differences usually point at generated, wrapped, or analysis-only source differences; do not treat them as automatic bugs.

`compile depth`
: `0` means no upstream module deps inside the selected graph. `N` means the longest upstream direct-dependency path has `N` edges. The target module appears after its dependencies.

`classpath reachability`
: Start at dependency classes directly referenced by current source, then follow Zinc class dependencies through internal dependency modules. `not reached` means present on the dependency classpath but not reachable from those roots.

`compile waste`
: `wasted delta sources = delta sources - reachable delta sources`. Use it to identify direct edges or transitive rows that make clients compile source files they do not reach.

`downstream usage`
: The inverse of running `strictDepsCompileDepth` on many clients. Start with one upstream module and compare each downstream client by the target module's directly referenced classes, reachable classes, and reachable sources.

`who introduces`
: Given `target`, each row says: "this direct dep opens a path to that transitive module." A row like `appB  appB -> shared -> target` means `appA` sees `target` because `appA` directly depends on `appB`. Use this to decide which direct edge to remove, narrow, or move; do not add `target` as a direct dep unless current source actually uses its classes.

## Module Settings

Override these in a module when needed:

```scala
object appA extends ScalaModule with StrictDepsModule {
  override def strictDepsIgnoredModuleDeps = Task {
    Seq("generatedBridge")
  }
  override def strictDepsMaxClassesPerModule = Task {
    20
  }
  override def strictDepsFailOnUnusedDirectModuleDeps = Task {
    true
  }
  override def strictDepsFailOnMissingDirectModuleDeps = Task {
    true
  }
}
```

`strictDepsIgnoredModuleDeps`
: Module names to ignore in reports and checks. Use sparingly, with a reason in surrounding code when possible.

`strictDepsMaxClassesPerModule`
: Maximum class names shown per module in Markdown report and fix plan. Default `12`.

`strictDepsFailOnUnusedDirectModuleDeps`
: Whether `strictDepsCheck` fails on unused direct deps. Default `true`.

`strictDepsFailOnMissingDirectModuleDeps`
: Whether `strictDepsCheck` fails on missing direct deps. Default `true`.

## Practical Workflow

1. Run `strictDepsJsonReport` for exact facts.
2. Read `strictDepsFixPlan` for the direct add/remove proposal.
3. Run `strictDepsAutofixPlan` or `strictDepsApplyFix --dryRun true` when the proposal should be applied mechanically.
4. Run `strictDepsApplyFix` only after reviewing the plan. Rerun the same report/check after applying.
5. Use `strictDepsWeight` if a removal could save a lot of compile input.
6. Use `strictDepsCompileWaste` when a dependency is real but suspiciously fat.
7. Use `strictDepsCompileDepth` when compile order or upstream layering matters.
8. Use `strictDepsWhoIntroduces --target targetModule` when an unwanted transitive module needs a direct-edge explanation.
9. Use global `strictDepsCommonAncestors` to find modules upstream of almost everything.
10. Use global `strictDepsCompileWaste` to find repeated waste across clients.

Do not treat `strictDepsFixPlan` as a command script. It is a receipt. Use `strictDepsAutofixPlan` or `strictDepsApplyFix --dryRun true` when you need an apply-ready plan, then verify with the same report/check after mutation.
