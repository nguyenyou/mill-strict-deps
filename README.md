# mill-strict-deps

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

## Install

`build.mill` header:

```scala
//| mvnDeps:
//| - io.github.nguyenyou::mill-strict-deps::0.1.0
```

The `::version` shorthand appends `_mill$MILL_BIN_PLATFORM`, so on Mill 1.x it
resolves to `mill-strict-deps_mill1`.

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
./mill appA.strictDepsCheck
```

Outputs:

```text
out/appA/strictDepsReport.dest/strict-deps-report.md
out/appA/strictDepsJsonReport.dest/strict-deps-report.json
out/appA/strictDepsFixPlan.dest/strict-deps-fix-plan.md
```

`strictDepsCheck` fails when the module has unused direct module deps or missing
direct module deps, depending on the module settings.

## Current Scope

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

## Local Development

```text
./mill strictdeps.compile
./mill strictdeps.test
./mill strictdeps.publishLocal
```

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
