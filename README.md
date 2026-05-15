# mill-strict-deps

![Scala3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.nguyenyou/mill-strict-deps_mill1_3)

[Bazel's Strict Java Deps and `unused_deps`](https://blog.bazel.build/2017/06/28/sjd-unused_deps.html)
starts from a simple question:

```text
What did this target say it needs?
What did the compiler prove it actually used?
What should we remove or add?
```

`mill-strict-deps` brings that idea to the Mill build tool. It reads internal
Mill module edges and Zinc analysis, then reports whether a module depends on
too much, or uses another module only through a transitive path.

## Why This Matters

In a large Mill monorepo, one careless dependency edge can quietly drag a whole
part of the build graph into the compile path.

Suppose `appA` needs one UI component. The component lives in `uiWidget`, but an
engineer notices that it is available through `appB-admin`, so they write:

```text
appA
 |
 v
appB-admin
 |
 +--> appC-core --> appC-feature1 --> ...
 |
 +--> appD-core --> appD-feature1 --> ...
 |
 +--> appE-core --> appE-feature1 --> ...
 |
 +--> uiWidget
```

The code works. The build graph is now lying.

`appA` source did not ask for an admin app, app C, app D, app E, and their
feature graphs. It asked for one widget. The cleaner shape is:

```text
appA --------> uiWidget

appB-admin --> uiWidget
          \
           +--> appC-core --> ...
           +--> appD-core --> ...
           +--> appE-core --> ...
```

That is the main selling point: in a large multi-module codebase, precise
internal module deps help avoid compiling files that the current app never truly
needed.

## What's "Strict Deps"?

Strict deps means:

```text
If source code in appA directly mentions a class from uiWidget,
then appA should directly declare uiWidget in moduleDeps or compileModuleDeps.
```

It should not rely on `uiWidget` only because `appB-admin` happens to bring it
in transitively.

In Mill terms, this is not strict enough:

```scala
object appA extends ScalaModule {
  def moduleDeps = Seq(appBAdmin)
}
```

If `appA` source uses `uiWidget.Button`, the strict version is:

```scala
object appA extends ScalaModule {
  def moduleDeps = Seq(uiWidget)
}
```

The rule is about compile-time source usage, not runtime packaging. If a module
is needed only at runtime, through reflection, resources, generated code, or a
framework convention, that edge may need an explicit suppression or a separate
runtime dependency story.

## What The Plugin Does

For each Mill JVM module, it compares two lists:

```text
declared direct internal deps
actually used internal deps from Zinc analysis
```

Then it reports:

```text
declared direct deps - actually used direct deps = unused direct deps
actually used transitive deps - declared direct deps = missing direct deps
```

For the widget example, it can report:

```text
unused direct dep:  appB-admin
missing direct dep: uiWidget
```

So the engineer gets a concrete fix plan:

```text
remove appB-admin
add uiWidget
```

It also reports advisory dependency reference weight:

```text
directly referenced classes from dependency / all internal dependency classes directly referenced by this module
directly referenced classes from dependency / all classes defined by that dependency
```

That helps separate "this module leans heavily on core" from "this module only
touches one class through a large dependency".

It also reports dependency source weight:

```text
absolute weight = source files in the module plus its transitive module deps
delta weight    = source files unique to this edge, after sibling deps are counted
```

For direct deps, delta weight is the number of source files that would leave the
compile graph if that edge were removed. For transitive missing deps, delta
weight is the number of new source files added by declaring that module directly,
which is often `0` because the files were already present through another edge.

For a quick dependency-size view without the full JSON report, `strictDepsWeight`
prints total source weight, then direct and transitive module deps sorted by
absolute weight:

```text
Mill allSourceFiles = source files planned for compiler input
Zinc allSources     = source files recorded in compile analysis

current module sources = this module's source count
dependency sources     = distinct source files from transitive module deps
total source weight    = distinct current module sources + dependency sources
own weight             = dependency module source files only
absolute weight        = dependency module source files
                         + source files from modules reachable from it
delta weight           = source files from this row's absolute weight
                         that were not already counted by earlier rows
own lines              = physical source lines in the dependency module itself
absolute lines         = physical source lines in the dependency module
                         plus modules reachable from it
delta lines            = source lines from this row's delta source files
own classes            = Zinc classes defined by the dependency module itself
directly referenced classes = classes from that dependency module directly
                         referenced by the current module, shown as count / total (%)
                         or zero when no class is directly referenced
reachable classes      = directly referenced classes plus Zinc class deps reachable
                         from them inside that dependency module
reachable sources      = source files in that dependency module that define
                         reachable classes
absolute classes       = Zinc classes defined by the dependency module
                         plus modules reachable from it
directly referenced dependency classes = dependency classes this module directly referenced
reachable dependency classes     = directly referenced classes plus Zinc class deps
                                   reachable from them, shown as reached / total (%)
reachable dependency sources     = source files that define reachable dependency classes,
                                   shown as reached / total (%)
```

The command calculates every count both ways. If Mill and Zinc agree, it prints
one number. If they differ, it prints both numbers as `Mill / Zinc` and adds a
note. File and line metrics are compared through both Mill planned sources and
Zinc analysis sources. Class metrics are Zinc-only because Mill's source list
does not know which classes the compiler produced. Absolute weights can overlap
because two dependency modules can share the same transitive dependency. Delta
weights are ranked-row contributions, so their sum is the distinct dependency
source count instead of double-counting overlap. Reachability rows are also
Zinc-only: they describe compile-analysis class reachability, not runtime
main-method reachability from a linker.

For a compile-order view, `strictDepsCompileDepth` prints the same module
source weights grouped from upstream to downstream:

```text
compile depth 0 = dependency modules with no upstream module deps in this graph
compile depth N = modules whose longest upstream direct-dependency path has N edges
target depth    = the examined module
delta weight    = source files first introduced by that row in top-down order
```

This view does not draw edges. It uses the direct module-dependency graph to
place nodes by compile depth, so the terminal output reads top down as compile order.
Pass `--zeroReachableSourcesOnly true` to keep only dependency rows where the
target has the module in its compile world but the `reachable sources` count is
zero.

For a whole-build view, `strictDepsCommonAncestors` prints which modules are
upstream of the most other modules:

```text
featureA -+
featureB -+--> commonCore
featureC -+

needed by  = how many other modules eventually depend on this module
comparable = all analyzed modules except this candidate module
coverage   = needed by / comparable
own weight = source files in this module itself
own lines  = physical source lines in this module itself
own classes = Zinc classes defined by this module itself
```

When `needed by == comparable`, that row is a common ancestor: every other
analyzed module eventually needs it. The command gathers
`__.strictDepsGraphSnapshot`, so the analyzed universe is the modules that mix in
`StrictDepsModule` plus the transitive module deps visible from their snapshots.

It also reports classpath reachability:

```text
direct roots       = dependency classes this module directly touched
reachable needed   = direct roots plus dependency classes reachable from them
not reached        = dependency module classes/sources outside that graph
```

Think of it like pouring ink into the graph at the classes the client touched:

```text
client source
   |
   v
Button ----> Theme ----> Color

given by dependency modules:
  Button, Theme, Color, AdminPage, BillingFlow

reachable needed:
  Button, Theme, Color

not reached:
  AdminPage, BillingFlow
```

The per-module reachability table helps find fat module edges: dependencies that
are real, but provide many classes or source files the client never reaches.

For a waste-first view, `strictDepsCompileWaste` shows the rows that are most
likely to be bad graph shape:

```text
delta sources           = unique dependency source files this row contributes
reachable delta sources = delta source files Zinc can reach from classes used
                          by the current client
wasted delta sources    = delta sources - reachable delta sources
wasted own sources      = source files in that dependency module not reached
introduced by           = direct dependency edge that pulled this row in
```

This is the view to use when the question is "which dependency edge made this
client compile files it did not really need?"

```text
client ----> direct dep ----> transitive dep
   |              |                 |
   |              +-----------------+
   |                    given sources
   |
   +---- Zinc class graph ----> reachable sources

waste = given sources - reachable sources
```

The global `strictDepsCompileWaste` command collects
`__.strictDepsCompileWasteSnapshot` and prints two hotspot tables:

```text
bad nodes = dependency modules with high repeated wasted delta
bad edges = client -> dependency rows with high wasted delta
```

The global `strictDepsDownstreamUsage` command answers the reverse question for
one dependency module:

```text
shared.ui.js
   ^
   |
clientA   clientB   clientC
```

It collects the same `__.strictDepsCompileWasteSnapshot` data, filters to the
target dependency, and prints one row per selected downstream client that has
the target in its compile world, with an unlabeled row-number column numbering
the sorted rows. The important columns match
`strictDepsCompileDepth`, including the colored progress bars:

```text
directly referenced classes = target classes directly referenced by the client
reachable classes           = target classes reachable from those direct roots
reachable sources           = target sources defining the reachable classes
```

Use this when you already know the upstream module and want to compare how much
each client actually needs from it. By default it collects all
`__.strictDepsCompileWasteSnapshot` tasks. To limit the universe to selected app
clients, pass one Mill selector expression positionally:

```text
./mill io.github.nguyenyou.millstrictdeps.strictDepsDownstreamUsage/ \
  --target shared.ui.js \
  '{clientA.js,clientB.js}.strictDepsCompileWasteSnapshot'
```

Use `strictDepsCommonAncestors` to find modules that are upstream of almost
everything. Use `strictDepsCompileWaste` to find which of those common or large
modules are actually wasting compile input for clients.

## Install

`build.mill` header:

```scala
//| mvnDeps:
//| - io.github.nguyenyou::mill-strict-deps::1.10.0
```

The `::version` shorthand appends `_mill$MILL_BIN_PLATFORM`, so on Mill 1.x it
resolves to the Maven Central artifact `mill-strict-deps_mill1_3`.

Mix the trait into a JVM module:

```scala
import io.github.nguyenyou.millstrictdeps.StrictDepsModule

object appA extends ScalaModule with StrictDepsModule {
  def scalaVersion = "3.8.3"
  def moduleDeps = Seq(appBAdmin)
}
```

## Tasks

```text
./mill appA.strictDepsReport
./mill appA.strictDepsJsonReport
./mill appA.strictDepsFixPlan
./mill appA.strictDepsAutofixPlan
./mill appA.strictDepsApplyFix --dryRun true
./mill appA.strictDepsApplyFix
./mill appA.strictDepsWeight
./mill appA.strictDepsCompileDepth
./mill appA.strictDepsCompileDepth --zeroReachableSourcesOnly true
./mill appA.strictDepsCompileWaste
./mill appA.strictDepsWhoIntroduces --target uiWidget
./mill appA.strictDepsCheck

# Global graph command. Collects every __.strictDepsGraphSnapshot.
./mill io.github.nguyenyou.millstrictdeps.strictDepsCommonAncestors/

# Global waste command. Collects every __.strictDepsCompileWasteSnapshot.
./mill io.github.nguyenyou.millstrictdeps.strictDepsCompileWaste/

# Global reverse-usage command for one dependency module.
./mill io.github.nguyenyou.millstrictdeps.strictDepsDownstreamUsage/ --target uiWidget
```

Outputs:

```text
out/appA/strictDepsReport.dest/strict-deps-report.md
out/appA/strictDepsJsonReport.dest/strict-deps-report.json
out/appA/strictDepsFixPlan.dest/strict-deps-fix-plan.md
out/appA/strictDepsAutofixPlan.dest/strict-deps-autofix-plan.md
```

`strictDepsJsonReport` is the comprehensive machine-readable report. It keeps
the strict-deps facts used by the Markdown report and also includes:

- `reachability`: Zinc class/source reachability by dependency module.
- `weightReport`: Mill and Zinc source counts, source-line counts, class
  counts, absolute weight, delta weight, compile-depth delta weight, and per-row
  usage/reachability percentages.
- `compileWaste`: the exact per-client data behind `strictDepsCompileWaste`,
  sorted by wasted delta source count.

`strictDepsCheck` fails when the module has unused direct module deps or missing
direct module deps, depending on the module settings.

`strictDepsAutofixPlan` writes the exact source edit plan that
`strictDepsApplyFix` would attempt. `strictDepsApplyFix --dryRun true` prints the
same plan without changing files. `strictDepsApplyFix` edits the module source
file only when every requested change is safe. If any add or remove cannot be
located exactly, the command fails without writing the file.

The autofix is deliberately narrow. It edits only the source file and module
line recorded by Mill for the current module. It supports explicit `moduleDeps`
and `compileModuleDeps` definitions whose right-hand side is `Seq(...)`,
`Seq.empty`, `Nil`, `super.moduleDeps ++ Seq(...)`, or
`Seq(...) ++ super.moduleDeps` (and the matching `compileModuleDeps` forms). It
can insert a missing dependency method for additions. It refuses computed deps,
cross modules, ambiguous removals, and any source shape where the plugin would
have to guess.

`strictDepsWhoIntroduces --target <target>` explains why a transitive module is
on the current module's compile classpath. It prints the shortest module chain
from each direct compile module dep to `target`. Direct deps that do not reach
`target` are omitted; if no direct dep reaches it, the command prints a one-line
message.

Use it after `strictDepsWeight`, `strictDepsCompileDepth`, or
`strictDepsCompileWaste` shows a suspicious transitive module and you need to
answer: "which direct edge brought this box into the room?"

```text
./mill appA.strictDepsWhoIntroduces --target uiWidget

target: uiWidget

direct dep  path
----------  ----------------------
appB-admin  appB-admin -> uiWidget
```

<details>
<summary>How To Read The Report Numbers</summary>

<br>

The summary numbers count dependency edges, not source files.

Think of one report as a receipt for one module:

```text
module under test: appA

declared direct boxes:       appB, uiWidget, logging
classes directly referenced: uiWidget.Button, logging.Logger, theme.Color
```

The report asks four questions:

| metric | what it counts | what it means |
| --- | ---: | --- |
| `used direct module deps` | direct internal modules that contributed at least one directly referenced class | Good. The module declared the box, and the compiler saw code reference pieces from that box. |
| `unused direct module deps` | direct internal modules with no directly referenced classes recorded by Zinc | Suspicious. The module declared the box, but the compiler did not see source code reference classes from it. This is often removable, unless the edge is needed for resources, reflection, generated code, framework conventions, or another non-classpath reason. |
| `missing direct module deps` | transitive internal modules whose classes were referenced directly | Bad graph shape. The source code referenced pieces from a box that was only available through another box. Add this module as a direct dep. |
| `dependency reference weight` | directly referenced classes per internal dependency, with percentages | Advisory coupling signal. It shows how much of the current module's internal dependency references come from each dependency, and how much of that dependency's class surface was referenced. |
| `dependency source weight` | source files carried by each internal dependency edge | Compile-cost signal. Absolute weight is the whole dependency box. Delta weight is what this edge uniquely adds or saves after shared transitive deps are counted. |
| `used library classpath entries` | external jars/classpath entries with usage recorded by Zinc | Informational today. External Maven deps are already compiled, so the current plugin does not fail on these. |

The detailed sections then explain the summary.

`Used Direct Module Deps` means:

```text
appA -> uiWidget

appA source mentions uiWidget.Button
```

That is a truthful edge.

`Unused Direct Module Deps` means:

```text
appA -> appB

appA source did not mention classes from appB
```

That edge may be overpull. Remove it if compilation and runtime behavior still
make sense.

`Missing Direct Module Deps` means:

```text
appA -> appB -> uiWidget

appA source mentions uiWidget.Button
```

The code compiles because `appB` brings `uiWidget` along for the ride, but the
graph is hiding what `appA` really needs. The fix is usually:

```text
appA -> uiWidget
```

In the detail tables, `directly referenced classes` is the number of class names
from that upstream module that Zinc saw the current module reference directly.
`sample` shows a capped list of examples; the cap is controlled by
`strictDepsMaxClassesPerModule` (default: `12`).

</details>

<details>
<summary>Current Scope</summary>

<br>

This project currently focuses on internal Mill module dependencies only:
`moduleDeps` and `compileModuleDeps`.

That is intentional. Internal deps are where a broad edge can force Mill to
compile many source files that the current module does not truly need. External
dependencies are already published as compiled `.class` files, so they usually
do not create the same source-compilation cost.

Implemented:

- Internal Scala/JVM and Java/JVM module-dep reporting through Zinc analysis.
- Mixed Scala/Java sources inside the same Mill `ScalaModule`.
- Markdown report.
- JSON fact report.
- Suggested fix plan that does not mutate `build.mill`.
- Safe all-or-nothing autofix for explicit `moduleDeps` and
  `compileModuleDeps` `Seq(...)` shapes.
- Check mode that fails on unused or missing direct module deps.

Planned:

- External Maven dependency reporting, later.
- Suppressions with reasons.
- Broader Scala source-shape support for safe autofix.
- CI-friendly baselines.
- Better diagnostics for resource-only, reflection, macro, and annotation
  processor cases.

</details>

<details>
<summary>Local Development</summary>

<br>

```text
./mill strictdeps.compile
./mill strictdeps.test
./mill strictdeps.publishLocal
```

</details>

<details>
<summary>Publishing</summary>

<br>

This project is configured for Sonatype Central publishing through Mill's
`SonatypeCentralPublishModule`.

Local Ivy publish:

```text
./mill strictdeps.publishLocal
```

Sonatype Central publish:

```text
./mill strictdeps.publishSonatypeCentral
```

Mill reads Sonatype credentials from:

```text
MILL_SONATYPE_USERNAME
MILL_SONATYPE_PASSWORD
```

Release publishing also needs signing credentials:

```text
MILL_PGP_SECRET_BASE64
MILL_PGP_PASSPHRASE
```

Mill can create and print the signing env vars with:

```text
./mill mill.scalalib.SonatypeCentralPublishModule/initGpgKeys
```

The published version is derived from git tags with `0.1.0` as the no-tag
fallback. Tag a release, for example `v0.1.0`, when you want Sonatype Central
to receive exactly `0.1.0`; untagged or dirty commits include git metadata in
the version.

</details>

<details>
<summary>How It Works In Mill</summary>

<br>

Bazel compares declared BUILD deps with `.jdeps` compiler facts. In Mill, this
plugin compares declared module edges with Zinc analysis:

- declared edges come from `moduleDeps` and `compileModuleDeps`
- usage facts come from the Zinc analysis file produced by Mill compilation
- output goes to a Markdown report, a JSON fact file, a fix plan, or a failing
  check

```text
Mill moduleDeps / compileModuleDeps
  |
  v
Zinc analysis says which upstream classes were touched
  |
  v
compare declared modules with used modules
  |
  v
Markdown report, JSON facts, fix plan, or failing check
```

</details>

<details>
<summary>Bazel To Mill Mapping</summary>

<br>

| Bazel idea | Mill implementation |
| --- | --- |
| `--direct_dependencies` from the Java compile action | `moduleDeps` and `compileModuleDeps` declared on the Mill module |
| `.jdeps` proto containing compile-time jar usage | `strictDepsJsonReport` generated from Zinc analysis |
| strict-deps compiler plugin detects indirect jars during javac | analyzer detects used transitive modules from Zinc relations |
| `unused_deps` emits Buildozer commands | `strictDepsFixPlan` emits suggested edits; `strictDepsApplyFix` applies safe supported edits |

The implementation copies Bazel's architecture, not its Java-specific compiler
plugin:

```text
detect facts first
report facts second
suggest edits third
mutate build files only after the autofix plan is trustworthy
```

</details>

<details>
<summary>Useful Bazel References</summary>

<br>

- [`unused_deps.go`](https://github.com/bazelbuild/buildtools/blob/master/unused_deps/unused_deps.go)
  reads javac params plus `.jdeps`, then prints Buildozer commands.
- [`deps.proto`](https://github.com/bazelbuild/bazel/blob/master/src/main/protobuf/deps.proto)
  is the structured dependency usage format behind `.jdeps`.
- [`JavaCompileActionBuilder`](https://github.com/bazelbuild/bazel/blob/master/src/main/java/com/google/devtools/build/lib/rules/java/JavaCompileActionBuilder.java)
  passes direct-dependency metadata into JavaBuilder.
- [`StrictJavaDepsPlugin`](https://github.com/bazelbuild/bazel/blob/master/src/java_tools/buildjar/java/com/google/devtools/build/buildjar/javac/plugins/dependency/StrictJavaDepsPlugin.java)
  checks whether directly referenced types came from indirect jars.
- [`DependencyModule`](https://github.com/bazelbuild/bazel/blob/master/src/java_tools/buildjar/java/com/google/devtools/build/buildjar/javac/plugins/dependency/DependencyModule.java)
  collects dependency facts and writes the `.jdeps` output.

</details>
