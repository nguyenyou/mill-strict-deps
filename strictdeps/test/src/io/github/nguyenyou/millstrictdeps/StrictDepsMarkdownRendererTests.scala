package io.github.nguyenyou.millstrictdeps

import utest.*

object StrictDepsMarkdownRendererTests extends TestSuite {
  def tests: Tests = Tests {
    test("renders report summary and module sections") {
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
  }
}

