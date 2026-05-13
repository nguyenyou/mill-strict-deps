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

It also reports advisory dependency usage weight:

```text
used classes from dependency / all internal dependency classes used by this module
used classes from dependency / all classes defined by that dependency
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
used classes           = classes from that dependency module directly referenced
                         by the current module, shown as used / total (%)
                         or zero when no class is used
absolute classes       = Zinc classes defined by the dependency module
                         plus modules reachable from it
direct used dependency classes   = dependency classes this module directly touched
reachable dependency classes     = direct used classes plus Zinc class deps
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

## Install

`build.mill` header:

```scala
//| mvnDeps:
//| - io.github.nguyenyou::mill-strict-deps::1.5.0
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
./mill appA.strictDepsWeight
./mill appA.strictDepsCompileDepth
./mill appA.strictDepsCheck

# Global graph command. Collects every __.strictDepsGraphSnapshot.
./mill io.github.nguyenyou.millstrictdeps.strictDepsCommonAncestors/
```

Outputs:

```text
out/appA/strictDepsReport.dest/strict-deps-report.md
out/appA/strictDepsJsonReport.dest/strict-deps-report.json
out/appA/strictDepsFixPlan.dest/strict-deps-fix-plan.md
```

`strictDepsCheck` fails when the module has unused direct module deps or missing
direct module deps, depending on the module settings.

<details>
<summary>How To Read The Report Numbers</summary>

<br>

The summary numbers count dependency edges, not source files.

Think of one report as a receipt for one module:

```text
module under test: appA

declared direct boxes:  appB, uiWidget, logging
classes actually used:  uiWidget.Button, logging.Logger, theme.Color
```

The report asks four questions:

| metric | what it counts | what it means |
| --- | ---: | --- |
| `used direct module deps` | direct internal modules that contributed at least one used class | Good. The module declared the box, and the compiler saw code use pieces from that box. |
| `unused direct module deps` | direct internal modules with no used classes recorded by Zinc | Suspicious. The module declared the box, but the compiler did not see source code use classes from it. This is often removable, unless the edge is needed for resources, reflection, generated code, framework conventions, or another non-classpath reason. |
| `missing direct module deps` | transitive internal modules whose classes were used directly | Bad graph shape. The source code used pieces from a box that was only available through another box. Add this module as a direct dep. |
| `dependency usage weight` | used classes per internal dependency, with percentages | Advisory coupling signal. It shows how much of the current module's internal dependency usage comes from each dependency, and how much of that dependency's class surface was touched. |
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

In the detail tables, `used classes` is the number of class names from that
upstream module that Zinc saw the current module touch. `sample` shows a capped
list of examples; the cap is controlled by `strictDepsMaxClassesPerModule`
(default: `12`).

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
- Check mode that fails on unused or missing direct module deps.

Planned:

- External Maven dependency reporting, later.
- Suppressions with reasons.
- Safe `build.mill` editing after fix plans are trustworthy.
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
| `unused_deps` emits Buildozer commands | `strictDepsFixPlan` emits suggested `build.mill` edits |

The implementation copies Bazel's architecture, not its Java-specific compiler
plugin:

```text
detect facts first
report facts second
suggest edits third
mutate build files only after the suggestions are trustworthy
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
