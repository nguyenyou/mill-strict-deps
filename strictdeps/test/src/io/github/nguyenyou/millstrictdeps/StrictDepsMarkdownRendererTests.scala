package io.github.nguyenyou.millstrictdeps

import ujson.read
import utest.*

object StrictDepsMarkdownRendererTests extends TestSuite {
  def tests: Tests = Tests {
    val report = StrictDepsReport(
      usedDirectModuleDeps = Seq(
        StrictDepsModuleUsage("api", Seq("com.example.Api", "com.example.Id"))
      ),
      unusedDirectModuleDeps = Seq("server"),
      missingDirectModuleDeps = Seq(
        StrictDepsModuleUsage("domain", Seq("com.example.User"))
      ),
      usedLibraryClasspathEntries = Seq("/tmp/example.jar")
    )

    test("renders markdown report summary and module sections") {
      val markdown = StrictDepsMarkdownRenderer.render(
        moduleName = "app",
        report = report,
        maxClassesPerModule = 1
      )

      assert(markdown.contains("# Strict Deps Report: app"))
      assert(markdown.contains("unused direct module deps | 1"))
      assert(markdown.contains("`server`"))
      assert(markdown.contains("`domain`"))
      assert(markdown.contains("... 1 more"))
    }

    test("renders structured json report") {
      val json = read(StrictDepsJsonRenderer.render("app", report))

      assert(json("schemaVersion").num == 1)
      assert(json("moduleName").str == "app")
      assert(json("hasProblems").bool)
      assert(json("summary")("unusedDirectModuleDeps").num == 1)
      assert(json("usedDirectModuleDeps")(0)("moduleName").str == "api")
      assert(json("missingDirectModuleDeps")(0)("usedClasses")(0).str == "com.example.User")
    }

    test("renders fix plan separately from report facts") {
      val fixPlan = StrictDepsFixPlanRenderer.render(
        moduleName = "app",
        report = report,
        maxClassesPerModule = 1
      )

      assert(fixPlan.contains("# Strict Deps Fix Plan: app"))
      assert(fixPlan.contains("Add `domain`"))
      assert(fixPlan.contains("Remove `server`"))
      assert(fixPlan.contains("This is a suggested edit plan"))
    }
  }
}
