package io.github.nguyenyou.millstrictdeps

import mill.api.Segment
import mill.api.Segments
import utest.*

object StrictDepsAutofixTests extends TestSuite {
  def tests: Tests = Tests {
    test("updates a simple moduleDeps Seq") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  override def moduleDeps = Seq(api, server, helper)
          |}
          |""".stripMargin
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq(ref("api"), ref("server"), ref("helper")),
        transitiveDeps = Seq(
          ref("api", directDependencyModuleNames = Seq("domain")),
          ref("server"),
          ref("helper"),
          ref("domain")
        ),
        missingDirectDeps = Seq("domain"),
        unusedDirectDeps = Seq("server")
      )

      assert(plan.canApply)
      assert(plan.edits.map(edit => edit.action -> edit.moduleName).toSet == Set("add" -> "domain", "remove" -> "server"))
      assert(plan.applyTo(source).contains("override def moduleDeps = Seq(api, helper, domain)"))
    }

    test("preserves multiline Seq shape") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  override def moduleDeps = Seq(
          |    api,
          |    server,
          |    helper
          |  )
          |}
          |""".stripMargin
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq(ref("api"), ref("server"), ref("helper")),
        transitiveDeps = Seq(
          ref("api", directDependencyModuleNames = Seq("domain")),
          ref("server"),
          ref("helper"),
          ref("domain")
        ),
        missingDirectDeps = Seq("domain"),
        unusedDirectDeps = Seq("server")
      )
      val updated = plan.applyTo(source)

      assert(plan.canApply)
      assert(updated.contains(
        """Seq(
          |    api,
          |    helper,
          |    domain
          |  )""".stripMargin
      ))
    }

    test("inserts missing moduleDeps method") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  def scalaVersion = "3.8.3"
          |}
          |""".stripMargin
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq(ref("api")),
        transitiveDeps = Seq(ref("api", directDependencyModuleNames = Seq("domain")), ref("domain")),
        missingDirectDeps = Seq("domain"),
        unusedDirectDeps = Seq.empty
      )
      val updated = plan.applyTo(source)

      assert(plan.canApply)
      assert(updated.contains("  override def moduleDeps = Seq(domain)\n}"))
    }

    test("adds through compileModuleDeps when only compile-only introducers exist") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  override def compileModuleDeps = Seq(api)
          |}
          |""".stripMargin
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq.empty,
        compileDirectDeps = Seq(ref("api")),
        transitiveDeps = Seq(ref("api", directDependencyModuleNames = Seq("domain")), ref("domain")),
        missingDirectDeps = Seq("domain"),
        unusedDirectDeps = Seq.empty
      )
      val updated = plan.applyTo(source)

      assert(plan.canApply)
      assert(updated.contains("override def compileModuleDeps = Seq(api, domain)"))
      assert(!updated.contains("override def moduleDeps"))
    }

    test("refuses dynamic moduleDeps shapes") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  override def moduleDeps = depsFor(crossValue)
          |}
          |""".stripMargin
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq(ref("server")),
        transitiveDeps = Seq(ref("server")),
        missingDirectDeps = Seq.empty,
        unusedDirectDeps = Seq("server")
      )

      assert(!plan.canApply)
      assert(plan.replacements.isEmpty)
      assert(plan.skips.exists(skip => skip.moduleName == "server" && skip.reason.contains("supported Seq")))
    }

    test("does not remove a bare name for a dependency in a different parent") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  override def moduleDeps = Seq(bar)
          |}
          |""".stripMargin
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq(ref("foo.bar", labels = Seq("foo", "bar"))),
        transitiveDeps = Seq(ref("foo.bar", labels = Seq("foo", "bar"))),
        missingDirectDeps = Seq.empty,
        unusedDirectDeps = Seq("foo.bar")
      )

      assert(!plan.canApply)
      assert(plan.replacements.isEmpty)
      assert(plan.skips.exists(_.reason.contains("no exact dependency expression")))
    }

    test("refuses synthesized cross module additions") {
      val source =
        """object app extends ScalaModule with StrictDepsModule {
          |  override def moduleDeps = Seq(api)
          |}
          |""".stripMargin
      val crossRef = StrictDepsAutofix.ModuleRef(
        moduleName = "bar.2_13",
        segments = Segments(Seq(Segment.Label("bar"), Segment.Cross(Seq("2.13")))),
        directDependencyModuleNames = Seq.empty
      )
      val plan = planFor(
        source = source,
        normalDirectDeps = Seq(ref("api")),
        transitiveDeps = Seq(ref("api", directDependencyModuleNames = Seq("bar.2_13")), crossRef),
        missingDirectDeps = Seq("bar.2_13"),
        unusedDirectDeps = Seq.empty
      )

      assert(!plan.canApply)
      assert(plan.replacements.isEmpty)
      assert(plan.skips.exists(_.reason.contains("cross module")))
    }
  }

  private def planFor(
      source: String,
      normalDirectDeps: Seq[StrictDepsAutofix.ModuleRef],
      transitiveDeps: Seq[StrictDepsAutofix.ModuleRef],
      missingDirectDeps: Seq[String],
      unusedDirectDeps: Seq[String],
      compileDirectDeps: Seq[StrictDepsAutofix.ModuleRef] = Seq.empty
  ): StrictDepsAutofix.Plan = {
    StrictDepsAutofix.plan(
      StrictDepsAutofix.Input(
        moduleName = "app",
        moduleSegments = Segments.labels("app"),
        sourceFile = os.pwd / "build.mill",
        moduleLine = 1,
        normalDirectDeps = normalDirectDeps,
        compileDirectDeps = compileDirectDeps,
        transitiveDeps = transitiveDeps,
        missingDirectDeps = missingDirectDeps,
        unusedDirectDeps = unusedDirectDeps
      ),
      source
    )
  }

  private def ref(
      moduleName: String,
      labels: Seq[String] = Seq.empty,
      directDependencyModuleNames: Seq[String] = Seq.empty
  ): StrictDepsAutofix.ModuleRef = {
    StrictDepsAutofix.ModuleRef(
      moduleName = moduleName,
      segments = Segments.labels((if (labels.isEmpty) Seq(moduleName) else labels)*),
      directDependencyModuleNames = directDependencyModuleNames
    )
  }
}
