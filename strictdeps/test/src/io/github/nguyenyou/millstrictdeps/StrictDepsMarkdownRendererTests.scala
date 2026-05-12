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
      usedLibraryClasspathEntries = Seq("/tmp/example.jar"),
      dependencyUsageWeights = Seq(
        StrictDepsModuleUsageWeight(
          moduleName = "api",
          declaredDirect = true,
          usedClasses = Seq("com.example.Api", "com.example.Id"),
          dependencyClassCount = 4,
          currentModuleUsagePercent = 66.7,
          dependencyTouchedPercent = 50.0
        ),
        StrictDepsModuleUsageWeight(
          moduleName = "domain",
          declaredDirect = false,
          usedClasses = Seq("com.example.User"),
          dependencyClassCount = 2,
          currentModuleUsagePercent = 33.3,
          dependencyTouchedPercent = 50.0
        )
      ),
      reachability = StrictDepsReachabilityReport(
        providedClassCount = 5,
        directUsedClassCount = 2,
        reachableClassCount = 3,
        unusedClassCount = 2,
        reachableClassPercent = 60.0,
        providedSourceCount = 5,
        directUsedSourceCount = 2,
        reachableSourceCount = 3,
        unusedSourceCount = 2,
        reachableSourcePercent = 60.0,
        modules = Seq(
          StrictDepsModuleReachability(
            moduleName = "api",
            declaredDirect = true,
            providedClasses = Seq("com.example.Api", "com.example.Id", "com.example.UnusedApi"),
            directUsedClasses = Seq("com.example.Api", "com.example.Id"),
            reachableClasses = Seq("com.example.Api", "com.example.Id"),
            unusedClasses = Seq("com.example.UnusedApi"),
            providedSources = Seq("/src/Api.scala", "/src/Id.scala", "/src/UnusedApi.scala"),
            directUsedSources = Seq("/src/Api.scala", "/src/Id.scala"),
            reachableSources = Seq("/src/Api.scala", "/src/Id.scala"),
            unusedSources = Seq("/src/UnusedApi.scala"),
            reachableClassPercent = 66.7,
            reachableSourcePercent = 66.7
          ),
          StrictDepsModuleReachability(
            moduleName = "domain",
            declaredDirect = false,
            providedClasses = Seq("com.example.User", "com.example.UnusedDomain"),
            directUsedClasses = Seq.empty,
            reachableClasses = Seq("com.example.User"),
            unusedClasses = Seq("com.example.UnusedDomain"),
            providedSources = Seq("/src/User.scala", "/src/UnusedDomain.scala"),
            directUsedSources = Seq.empty,
            reachableSources = Seq("/src/User.scala"),
            unusedSources = Seq("/src/UnusedDomain.scala"),
            reachableClassPercent = 50.0,
            reachableSourcePercent = 50.0
          )
        )
      )
    )

    test("renders markdown report summary and module sections") {
      val markdown = StrictDepsMarkdownRenderer.render(
        moduleName = "app",
        report = report,
        maxClassesPerModule = 1
      )

      assert(markdown.contains("# Strict Deps Report: app"))
      assert(markdown.contains("unused direct module deps | 1"))
      assert(markdown.contains("Classpath Reachability"))
      assert(markdown.contains("| reachable needed | 3 (60.0%) | 3 (60.0%) |"))
      assert(markdown.contains("| `api` | direct | 2 / 3 (66.7%) | 2 / 3 (66.7%) | 1 |"))
      assert(markdown.contains("Dependency Usage Weight"))
      assert(markdown.contains("| `api` | direct | 2 | 66.7% | 2 / 4 (50.0%)"))
      assert(markdown.contains("| `domain` | transitive | 1 | 33.3% | 1 / 2 (50.0%)"))
      assert(markdown.contains("`server`"))
      assert(markdown.contains("`domain`"))
      assert(markdown.contains("... 1 more"))
    }

    test("renders structured json report") {
      val json = read(StrictDepsJsonRenderer.render("app", report))

      assert(json("schemaVersion").num == 2)
      assert(json("moduleName").str == "app")
      assert(json("hasProblems").bool)
      assert(json("summary")("unusedDirectModuleDeps").num == 1)
      assert(json("summary")("reachableDependencyClasses").num == 3)
      assert(json("summary")("unusedDependencySources").num == 2)
      assert(json("usedDirectModuleDeps")(0)("moduleName").str == "api")
      assert(json("missingDirectModuleDeps")(0)("usedClasses")(0).str == "com.example.User")
      assert(json("dependencyUsageWeights")(0)("moduleName").str == "api")
      assert(json("dependencyUsageWeights")(0)("declaredDirect").bool)
      assert(json("dependencyUsageWeights")(0)("currentModuleUsagePercent").num == 66.7)
      assert(json("dependencyUsageWeights")(1)("dependencyTouchedPercent").num == 50.0)
      assert(json("reachability")("reachableClassPercent").num == 60.0)
      assert(json("reachability")("modules")(0)("unusedSources")(0).str == "/src/UnusedApi.scala")
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
