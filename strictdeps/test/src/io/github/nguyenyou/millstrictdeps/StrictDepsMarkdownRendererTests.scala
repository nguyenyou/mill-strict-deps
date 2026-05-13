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
      dependencyWeights = Seq(
        StrictDepsModuleDependencyWeight(
          moduleName = "api",
          declaredDirect = true,
          directDependencyModuleNames = Seq("domain"),
          transitiveDependencyModuleNames = Seq("domain"),
          ownSourceCount = 2,
          absoluteSourceCount = 4,
          deltaSourceCount = 4,
          deltaKind = "remove"
        ),
        StrictDepsModuleDependencyWeight(
          moduleName = "domain",
          declaredDirect = false,
          directDependencyModuleNames = Seq.empty,
          transitiveDependencyModuleNames = Seq.empty,
          ownSourceCount = 2,
          absoluteSourceCount = 2,
          deltaSourceCount = 0,
          deltaKind = "add"
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
      assert(markdown.contains("Dependency Source Weight"))
      assert(markdown.contains("| `api` | direct | 2 | 4 | 4 (remove) | 1 | `domain` |"))
      assert(markdown.contains("| `domain` | transitive | 2 | 2 | 0 (add) | 0 |"))
      assert(markdown.contains("Dependency Usage Weight"))
      assert(markdown.contains("| `api` | direct | 2 | 66.7% | 2 / 4 (50.0%)"))
      assert(markdown.contains("| `domain` | transitive | 1 | 33.3% | 1 / 2 (50.0%)"))
      assert(markdown.contains("`server`"))
      assert(markdown.contains("`domain`"))
      assert(markdown.contains("... 1 more"))
    }

    test("renders structured json report") {
      val json = read(StrictDepsJsonRenderer.render("app", report))

      assert(json("schemaVersion").num == 3)
      assert(json("moduleName").str == "app")
      assert(json("hasProblems").bool)
      assert(json("summary")("unusedDirectModuleDeps").num == 1)
      assert(json("summary")("reachableDependencyClasses").num == 3)
      assert(json("summary")("unusedDependencySources").num == 2)
      assert(json("summary")("dependencyWeightModules").num == 2)
      assert(json("usedDirectModuleDeps")(0)("moduleName").str == "api")
      assert(json("missingDirectModuleDeps")(0)("usedClasses")(0).str == "com.example.User")
      assert(json("dependencyUsageWeights")(0)("moduleName").str == "api")
      assert(json("dependencyUsageWeights")(0)("declaredDirect").bool)
      assert(json("dependencyUsageWeights")(0)("currentModuleUsagePercent").num == 66.7)
      assert(json("dependencyUsageWeights")(1)("dependencyTouchedPercent").num == 50.0)
      assert(json("dependencyWeights")(0)("moduleName").str == "api")
      assert(json("dependencyWeights")(0)("absoluteSourceCount").num == 4)
      assert(json("dependencyWeights")(0)("deltaSourceCount").num == 4)
      assert(json("dependencyWeights")(0)("deltaKind").str == "remove")
      assert(json("dependencyWeights")(0)("directDependencyModuleNames")(0).str == "domain")
      assert(json("reachability")("reachableClassPercent").num == 60.0)
      assert(json("reachability")("modules")(0)("unusedSources")(0).str == "/src/UnusedApi.scala")
    }

    test("renders absolute dependency source weight list") {
      val markdown = StrictDepsWeightRenderer.render(
        moduleName = "app",
        report = StrictDepsWeightReport(
          currentModuleSources = StrictDepsSourceWeightComparison(3, 3),
          dependencySources = StrictDepsSourceWeightComparison(4, 4),
          totalSources = StrictDepsSourceWeightComparison(7, 7),
          dependencyWeights = report.dependencyWeights.map { weight =>
            StrictDepsModuleWeightComparison(
              moduleName = weight.moduleName,
              declaredDirect = weight.declaredDirect,
              ownSources = StrictDepsSourceWeightComparison(
                millSourceCount = weight.ownSourceCount,
                zincSourceCount = weight.ownSourceCount
              ),
              absoluteSources = StrictDepsSourceWeightComparison(
                millSourceCount = weight.absoluteSourceCount,
                zincSourceCount = weight.absoluteSourceCount
              )
            )
          }
        )
      )

      assert(!markdown.contains("Dependency Source Weights: app"))
      assert(!markdown.contains("How calculated"))
      assert(!markdown.contains("Mill allSourceFiles gives the source files planned for compiler input"))
      assert(!markdown.contains("Mill and Zinc source counts match."))
      assert(markdown.contains("current module sources      3"))
      assert(markdown.contains("dependency sources          4"))
      assert(markdown.contains("total source weight"))
      assert(markdown.contains("own weight  absolute weight"))
      assert(markdown.contains("api"))
      assert(markdown.contains("domain"))
      assert(!markdown.contains("delta"))
    }

    test("renders Mill and Zinc source weight differences") {
      val markdown = StrictDepsWeightRenderer.render(
        moduleName = "app",
        report = StrictDepsWeightReport(
          currentModuleSources = StrictDepsSourceWeightComparison(3, 2),
          dependencySources = StrictDepsSourceWeightComparison(4, 4),
          totalSources = StrictDepsSourceWeightComparison(7, 6),
          dependencyWeights = Seq(
            StrictDepsModuleWeightComparison(
              moduleName = "api",
              declaredDirect = true,
              ownSources = StrictDepsSourceWeightComparison(2, 1),
              absoluteSources = StrictDepsSourceWeightComparison(4, 3)
            )
          )
        )
      )

      assert(markdown.contains("3 Mill / 2 Zinc"))
      assert(markdown.contains("7 Mill / 6 Zinc"))
      assert(markdown.contains("2 Mill / 1 Zinc"))
      assert(markdown.contains("4 Mill / 3 Zinc"))
      assert(markdown.contains("Mill-Zinc +1"))
      assert(markdown.contains("own Mill-Zinc +1; absolute Mill-Zinc +1"))
      assert(markdown.contains("Differences usually mean generated or wrapped sources"))
    }

    test("renders compile waves top down") {
      val domainWeight = StrictDepsModuleWeightComparison(
        moduleName = "domain",
        declaredDirect = false,
        ownSources = StrictDepsSourceWeightComparison(2, 2),
        absoluteSources = StrictDepsSourceWeightComparison(2, 2)
      )
      val apiWeight = StrictDepsModuleWeightComparison(
        moduleName = "modules.reallyLong.api",
        declaredDirect = true,
        ownSources = StrictDepsSourceWeightComparison(2, 2),
        absoluteSources = StrictDepsSourceWeightComparison(4, 4)
      )
      val markdown = StrictDepsCompileWavesRenderer.render(
        moduleName = "app",
        report = StrictDepsWeightReport(
          currentModuleSources = StrictDepsSourceWeightComparison(3, 3),
          dependencySources = StrictDepsSourceWeightComparison(4, 4),
          totalSources = StrictDepsSourceWeightComparison(7, 7),
          dependencyWeights = Seq(apiWeight, domainWeight),
          compileWaves = Seq(
            StrictDepsCompileWave(0, Seq(domainWeight)),
            StrictDepsCompileWave(1, Seq(apiWeight))
          ),
          targetWaveIndex = 2
        )
      )

      assert(markdown.contains("metric                  count"))
      assert(!markdown.contains("compile wave 0"))
      assert(!markdown.contains("target wave 2"))
      assert(markdown.contains("own weight  absolute weight"))
      assert(markdown.contains("wave 0"))
      assert(markdown.contains("1 module"))
      assert(markdown.contains("wave 1"))
      assert(markdown.contains("target"))
      assert(markdown.contains("wave 2"))
      assert(markdown.contains("app"))

      val lines = markdown.linesIterator.toSeq
      val tableHeader = lines.find(line => line.startsWith("wave") && line.contains("module")).getOrElse("")
      assert(tableHeader.contains("relationship"))
      val continuousSeparators = lines.filter(line => line.nonEmpty && line.forall(_ == '-'))
      assert(continuousSeparators.size == 2)
      assert(continuousSeparators.forall(_.length > 20))

      val compileWaveLines = lines.dropWhile(!_.startsWith("wave 0"))
      assert(!compileWaveLines.exists(_.contains("--  --")))

      val waveHeaders = lines.filter(line => line.contains("module") && line.contains("relationship"))
      assert(waveHeaders.size == 1)
      assert(waveHeaders.map(_.indexOf("relationship")).distinct.size == 1)

      val domainRow = lines.find(_.contains("domain")).getOrElse("")
      val apiRow = lines.find(_.contains("modules.reallyLong.api")).getOrElse("")
      assert(domainRow.indexOf("transitive") == apiRow.indexOf("direct"))

      val targetRow = lines.find(_.startsWith("target")).getOrElse("")
      val targetWaveRow = lines.find(_.startsWith("wave 2")).getOrElse("")
      assert(targetRow.contains("app"))
      assert(targetRow.contains("target"))
      assert(targetWaveRow.nonEmpty)
      assert(lines.forall(line => !line.endsWith(" ")))
    }

    test("renders fix plan separately from report facts") {
      val fixPlan = StrictDepsFixPlanRenderer.render(
        moduleName = "app",
        report = report,
        maxClassesPerModule = 1
      )

      assert(fixPlan.contains("# Strict Deps Fix Plan: app"))
      assert(fixPlan.contains("Add `domain`"))
      assert(fixPlan.contains("absolute weight: 2 sources, delta weight: 0 sources add"))
      assert(fixPlan.contains("Remove `server`"))
      assert(fixPlan.contains("This is a suggested edit plan"))
    }
  }
}
