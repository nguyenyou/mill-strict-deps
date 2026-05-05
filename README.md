# mill-strict-deps

[Bazel's Strict Java Deps and `unused_deps`](https://blog.bazel.build/2017/06/28/sjd-unused_deps.html)
shows a simple idea:

```text
declared direct deps
  + compiler facts about what was actually used
  -> unused deps, missing direct deps, and fix suggestions
```

`mill-strict-deps` implements that idea for the Mill build tool.

Instead of Bazel BUILD targets, it reads Mill `moduleDeps`. Instead of Bazel
`.jdeps`, it reads Zinc analysis from Mill JVM modules. The goal is the same:
help each module declare the dependencies it actually uses, no more and no
less.

```text
Mill moduleDeps
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

## What It Finds

```text
declared direct deps - actually used direct deps = unused direct deps
actually used transitive deps - declared direct deps = missing direct deps
```

Example:

```text
app declares: api, server
app uses:    api, domain

unused:  server
missing: domain
```

Think of each Mill module as a box of blocks. This plugin checks whether the
current box asks for boxes it never opens, or quietly takes blocks through
another box instead of depending on the right box directly.

## What's "Strict Deps"?

In a Mill JVM build, strict deps means:

```text
If module app directly mentions a class from module domain,
then app should directly declare domain in moduleDeps or compileModuleDeps.
```

It should not rely on `domain` only because another dependency happens to bring
it in transitively.

```text
app
 |
 v
api
 |
 v
domain
```

If `app` source code uses `domain.User`, then this is not strict enough:

```scala
object app extends ScalaModule {
  def moduleDeps = Seq(api)
}
```

The strict version is:

```scala
object app extends ScalaModule {
  def moduleDeps = Seq(api, domain)
}
```

The rule is about compile-time source usage, not runtime packaging. If a module
is needed only at runtime, through reflection, resources, generated code, or a
framework convention, that edge may need an explicit suppression or a separate
runtime dependency story.

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
./mill app.strictDepsJsonReport
./mill app.strictDepsFixPlan
./mill app.strictDepsCheck
```

Outputs:

```text
out/app/strictDepsReport.dest/strict-deps-report.md
out/app/strictDepsJsonReport.dest/strict-deps-report.json
out/app/strictDepsFixPlan.dest/strict-deps-fix-plan.md
```

`strictDepsCheck` fails when the module has unused direct module deps or missing
direct module deps, depending on the module settings.

## Bazel To Mill Mapping

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

## Useful Bazel References

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

Implemented:

- Scala/JVM and Java/JVM module-dep reporting through Zinc analysis.
- Mixed Scala/Java sources inside the same Mill `ScalaModule`.
- Markdown report.
- JSON fact report.
- Suggested fix plan that does not mutate `build.mill`.
- Check mode that fails on unused or missing direct module deps.

Planned:

- Maven dependency reporting.
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
