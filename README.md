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

