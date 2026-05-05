# mill-strict-deps

A Mill plugin that reads Zinc analysis and reports JVM module dependency shape:

```text
declared direct deps - actually used direct deps = unused direct deps
actually used transitive deps - declared direct deps = missing direct deps
```

It is a report-first tool. It helps find build graph waste before enforcing it
in CI.

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

object app extends ScalaModule with StrictDepsModule {
  def scalaVersion = "3.8.3"
  def moduleDeps = Seq(api, server)
}
```

## Tasks

```text
./mill app.strictDepsReport
./mill app.strictDepsCheck
```

`strictDepsReport` writes:

```text
out/app/strictDepsReport.dest/strict-deps-report.md
```

The report currently covers Mill module dependencies:

| section | meaning |
| --- | --- |
| unused direct module deps | Direct deps declared by this module but not used by its compiled sources |
| missing direct module deps | Transitive deps whose classes are used without being declared directly |
| used direct module deps | Direct deps with classes referenced by this module |

## Mental Model

```text
app
 |
 v
declared boxes
 |
 v
Zinc analysis says which upstream classes were actually touched
 |
 v
compare declared boxes with touched boxes
```

This plugin does not redesign your modules. It is the scale on the sorting
table: it tells you which boxes were packed but not consumed, and which boxes
were consumed through someone else's box.

## Prior Art

This project follows the same basic idea as Bazel's
[Strict Java Deps and `unused_deps`](https://blog.bazel.build/2017/06/28/sjd-unused_deps.html).
Bazel's Java flow has four useful parts:

```text
BUILD deps
  |
  v
javac params say which jars are direct deps
  |
  v
compiler records which jars were actually used into .jdeps
  |
  v
unused_deps compares both lists and prints buildozer fixes
```

The Mill version maps those ideas like this:

| Bazel idea | Mill strict-deps equivalent |
| --- | --- |
| `--direct_dependencies` from the Java compile action | `moduleDeps` declared on the Mill module |
| `.jdeps` proto containing compile-time jar usage | Zinc analysis containing used class relations |
| strict-deps compiler plugin detects indirect jars during javac | report phase detects used transitive modules from Zinc analysis |
| `unused_deps` emits Buildozer commands | future fixer can emit suggested `build.mill` edits |

Implementation details worth learning from Bazel:

- Keep the compiler classpath broad enough that diagnosis can run without
  breaking ordinary symbol resolution first.
- Record dependency usage as structured data, not console text.
- Separate detection from editing: first produce facts, then produce safe fix
  commands.
- Include enough ownership information to say exactly which dependency to add
  or remove.
- Treat generated code, reflection, annotation processors, and runtime-only
  dependencies as explicit edge cases, not afterthoughts.

Useful source references:

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

## Current Scope

Implemented first:

- Scala/JVM and Java/JVM module-dep reporting through Zinc analysis.
- Report mode.
- Check mode that fails on unused or missing direct module deps.

Planned:

- Maven dependency reporting.
- Suppressions with reasons.
- JSON output.
- CI-friendly baselines.
- Better diagnostics for resource-only, reflection, macro, and annotation
  processor cases.

## Local Development

```text
./mill strictdeps.compile
./mill strictdeps.test
./mill strictdeps.publishLocal
```
