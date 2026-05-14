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
      assert(markdown.contains("own lines | absolute lines | delta lines | own classes | absolute classes"))
      assert(markdown.contains("| `api` | direct | 2 | 4 | 4 (remove) | 0 | 0 | 0 | 0 | 0 | 1 | `domain` |"))
      assert(markdown.contains("| `domain` | transitive | 2 | 2 | 0 (add) | 0 | 0 | 0 | 0 | 0 | 0 |"))
      assert(markdown.contains("Dependency Usage Weight"))
      assert(markdown.contains("| `api` | direct | 2 | 66.7% | 2 / 4 (50.0%)"))
      assert(markdown.contains("| `domain` | transitive | 1 | 33.3% | 1 / 2 (50.0%)"))
      assert(markdown.contains("`server`"))
      assert(markdown.contains("`domain`"))
      assert(markdown.contains("... 1 more"))
    }

    test("renders structured json report") {
      val apiWeight = StrictDepsModuleWeightComparison(
        moduleName = "api",
        declaredDirect = true,
        ownSources = StrictDepsSourceWeightComparison(2, 2),
        absoluteSources = StrictDepsSourceWeightComparison(4, 4),
        deltaSources = StrictDepsSourceWeightComparison(4, 4),
        compileDepthDeltaSources = StrictDepsSourceWeightComparison(2, 2),
        ownSourceLines = StrictDepsSourceWeightComparison(20, 20),
        absoluteSourceLines = StrictDepsSourceWeightComparison(40, 40),
        deltaSourceLines = StrictDepsSourceWeightComparison(40, 40),
        compileDepthDeltaSourceLines = StrictDepsSourceWeightComparison(20, 20),
        ownClassCount = 3,
        absoluteClassCount = 5,
        usedClassCount = 2,
        usedClassTotalCount = 3,
        usedClassPercent = 66.7,
        reachableClassCount = 2,
        reachableClassTotalCount = 3,
        reachableClassPercent = 66.7,
        reachableSourceCount = 2,
        reachableSourceTotalCount = 3,
        reachableSourcePercent = 66.7,
        reachableDeltaSourceCount = 2,
        wastedDeltaSourceCount = 2,
        wastedDeltaSourcePercent = 50.0,
        wastedOwnSourceCount = 1,
        wastedClassCount = 1,
        introducedByModuleNames = Seq("api")
      )
      val domainWeight = StrictDepsModuleWeightComparison(
        moduleName = "domain",
        declaredDirect = false,
        ownSources = StrictDepsSourceWeightComparison(2, 2),
        absoluteSources = StrictDepsSourceWeightComparison(2, 2),
        deltaSources = StrictDepsSourceWeightComparison(0, 0),
        compileDepthDeltaSources = StrictDepsSourceWeightComparison(2, 2),
        ownSourceLines = StrictDepsSourceWeightComparison(12, 12),
        absoluteSourceLines = StrictDepsSourceWeightComparison(12, 12),
        deltaSourceLines = StrictDepsSourceWeightComparison(0, 0),
        compileDepthDeltaSourceLines = StrictDepsSourceWeightComparison(12, 12),
        ownClassCount = 2,
        absoluteClassCount = 2,
        usedClassCount = 1,
        usedClassTotalCount = 2,
        usedClassPercent = 50.0,
        reachableClassCount = 1,
        reachableClassTotalCount = 2,
        reachableClassPercent = 50.0,
        reachableSourceCount = 1,
        reachableSourceTotalCount = 2,
        reachableSourcePercent = 50.0,
        introducedByModuleNames = Seq("api")
      )
      val weightReport = StrictDepsWeightReport(
        currentModuleSources = StrictDepsSourceWeightComparison(3, 3),
        dependencySources = StrictDepsSourceWeightComparison(4, 4),
        totalSources = StrictDepsSourceWeightComparison(7, 7),
        currentModuleSourceLines = StrictDepsSourceWeightComparison(30, 30),
        dependencySourceLines = StrictDepsSourceWeightComparison(52, 52),
        totalSourceLines = StrictDepsSourceWeightComparison(82, 82),
        currentModuleClassCount = 2,
        dependencyClassCount = 5,
        totalClassCount = 7,
        dependencyWeights = Seq(apiWeight, domainWeight),
        compileDepths = Seq(
          StrictDepsCompileDepth(0, Seq(domainWeight)),
          StrictDepsCompileDepth(1, Seq(apiWeight))
        ),
        targetDepthIndex = 2,
        reachability = report.reachability
      )
      val compileWaste = StrictDepsAnalyzer.compileWasteSnapshot("app", weightReport)
      val json = read(
        StrictDepsJsonRenderer.render(
          moduleName = "app",
          report = report,
          weightReport = Some(weightReport),
          compileWaste = Some(compileWaste)
        )
      )

      assert(json("schemaVersion").num == 4)
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
      assert(json("weightReport")("currentModuleSources")("millSourceCount").num == 3)
      assert(json("weightReport")("currentModuleSources")("zincSourceCount").num == 3)
      assert(json("weightReport")("currentModuleSources")("matches").bool)
      assert(json("weightReport")("dependencyWeights")(0)("relationship").str == "direct")
      assert(json("weightReport")("dependencyWeights")(0)("deltaSources")("zincSourceCount").num == 4)
      assert(json("weightReport")("dependencyWeights")(0)("wastedDeltaSourceCount").num == 2)
      assert(json("weightReport")("dependencyWeights")(0)("introducedByModuleNames")(0).str == "api")
      assert(json("weightReport")("compileDepths")(0)("index").num == 0)
      assert(json("weightReport")("targetDepthIndex").num == 2)
      assert(json("compileWaste")("moduleName").str == "app")
      assert(json("compileWaste")("deltaSourceCount").num == 4)
      assert(json("compileWaste")("wastedDeltaSourceCount").num == 2)
      assert(json("compileWaste")("dependencies")(0)("moduleName").str == "api")
      assert(json("compileWaste")("dependencies")(0)("wastedDeltaSourceCount").num == 2)
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
              ),
              deltaSources = StrictDepsSourceWeightComparison(
                millSourceCount = weight.deltaSourceCount,
                zincSourceCount = weight.deltaSourceCount
              ),
              ownClassCount = weight.ownSourceCount,
              absoluteClassCount = weight.absoluteSourceCount,
              usedClassCount = if (weight.moduleName == "api") 1 else 0,
              usedClassTotalCount = weight.ownSourceCount,
              usedClassPercent = if (weight.moduleName == "api") 50.0 else 0.0
            )
          },
          reachability = report.reachability
        )
      )

      assert(!markdown.contains("Dependency Source Weights: app"))
      assert(!markdown.contains("How calculated"))
      assert(!markdown.contains("Mill allSourceFiles gives the source files planned for compiler input"))
      assert(!markdown.contains("Mill and Zinc source counts match."))
      assert(markdown.linesIterator.exists(line => line.startsWith("current module sources") && line.trim.endsWith("3")))
      assert(markdown.linesIterator.exists(line => line.startsWith("dependency sources") && line.trim.endsWith("4")))
      assert(markdown.contains("total source weight"))
      assert(
        markdown.linesIterator.exists(line =>
          line.startsWith("reachable dependency classes") && line.trim.endsWith("3 / 5 (60.0%)")
        )
      )
      assert(
        markdown.linesIterator.exists(line =>
          line.startsWith("reachable dependency sources") && line.trim.endsWith("3 / 5 (60.0%)")
        )
      )
      assert(markdown.contains("own weight  absolute weight  delta weight"))
      val tableHeader = markdown.linesIterator.find(line => line.startsWith("module") && line.contains("relationship")).getOrElse("")
      assert(tableHeader.contains("own lines"))
      assert(tableHeader.contains("used classes"))
      assert(tableHeader.contains("reachable classes"))
      assert(tableHeader.contains("reachable sources"))
      assert(tableHeader.contains("absolute classes"))
      assert(markdown.contains("api"))
      assert(markdown.contains("domain"))
      val apiRow = markdown.linesIterator.find(_.startsWith("api")).getOrElse("")
      val domainRow = markdown.linesIterator.find(_.startsWith("domain")).getOrElse("")
      val apiTokens = apiRow.trim.split("\\s+").toSeq
      val domainTokens = domainRow.trim.split("\\s+").toSeq
      assert(apiRow.contains("direct"))
      assert(apiTokens.take(5) == Seq("api", "direct", "2", "4", "4"))
      assert(apiRow.contains("1 / 2 (50.0%)"))
      assert(apiRow.contains("2 / 3 (66.7%)"))
      assert(domainRow.contains("transitive"))
      assert(domainTokens.take(5) == Seq("domain", "transitive", "2", "2", "0"))
      assert(domainRow.contains("zero"))
      assert(!domainRow.contains("0 / 2"))
      assert(domainRow.contains("1 / 2 (50.0%)"))
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
              absoluteSources = StrictDepsSourceWeightComparison(4, 3),
              deltaSources = StrictDepsSourceWeightComparison(4, 2)
            )
          )
        )
      )

      assert(markdown.contains("3 Mill / 2 Zinc"))
      assert(markdown.contains("7 Mill / 6 Zinc"))
      assert(markdown.contains("2 Mill / 1 Zinc"))
      assert(markdown.contains("4 Mill / 3 Zinc"))
      assert(markdown.contains("4 Mill / 2 Zinc"))
      assert(markdown.contains("Mill-Zinc +1"))
      assert(markdown.contains("own weight Mill-Zinc +1; absolute weight Mill-Zinc +1; delta weight Mill-Zinc +2"))
      assert(markdown.contains("Differences usually mean generated or wrapped sources"))
    }

    test("renders compile depths top down") {
      val domainWeight = StrictDepsModuleWeightComparison(
        moduleName = "domain",
        declaredDirect = false,
        ownSources = StrictDepsSourceWeightComparison(2, 2),
        absoluteSources = StrictDepsSourceWeightComparison(2, 2),
        compileDepthDeltaSources = StrictDepsSourceWeightComparison(2, 2),
        ownClassCount = 2,
        absoluteClassCount = 2,
        usedClassCount = 1,
        usedClassTotalCount = 2,
        usedClassPercent = 50.0,
        reachableClassCount = 1,
        reachableClassTotalCount = 2,
        reachableClassPercent = 50.0,
        reachableSourceCount = 1,
        reachableSourceTotalCount = 2,
        reachableSourcePercent = 50.0
      )
      val apiWeight = StrictDepsModuleWeightComparison(
        moduleName = "modules.reallyLong.api",
        declaredDirect = true,
        ownSources = StrictDepsSourceWeightComparison(2, 2),
        absoluteSources = StrictDepsSourceWeightComparison(4, 4),
        compileDepthDeltaSources = StrictDepsSourceWeightComparison(2, 2),
        ownClassCount = 2,
        absoluteClassCount = 4,
        usedClassCount = 0,
        usedClassTotalCount = 2,
        usedClassPercent = 0.0,
        reachableClassCount = 0,
        reachableClassTotalCount = 2,
        reachableClassPercent = 0.0,
        reachableSourceCount = 0,
        reachableSourceTotalCount = 2,
        reachableSourcePercent = 0.0
      )
      val markdown = StrictDepsCompileDepthRenderer.render(
        moduleName = "app",
        report = StrictDepsWeightReport(
          currentModuleSources = StrictDepsSourceWeightComparison(3, 3),
          dependencySources = StrictDepsSourceWeightComparison(4, 4),
          totalSources = StrictDepsSourceWeightComparison(7, 7),
          dependencyWeights = Seq(apiWeight, domainWeight),
          compileDepths = Seq(
            StrictDepsCompileDepth(0, Seq(domainWeight)),
            StrictDepsCompileDepth(1, Seq(apiWeight))
          ),
          targetDepthIndex = 2,
          reachability = report.reachability
        )
      )

      assert(markdown.linesIterator.exists(line => line.startsWith("metric") && line.contains("count")))
      assert(
        markdown.linesIterator.exists(line =>
          line.startsWith("reachable dependency classes") && line.trim.endsWith("3 / 5 (60.0%)")
        )
      )
      assert(markdown.contains("own sources  absolute sources  delta sources"))
      val tableHeader = markdown.linesIterator.find(line => line.startsWith("depth") && line.contains("module")).getOrElse("")
      assert(tableHeader.contains("own sources"))
      assert(tableHeader.contains("absolute sources"))
      assert(tableHeader.contains("delta sources"))
      assert(tableHeader.contains("own lines"))
      assert(tableHeader.contains("used classes"))
      assert(tableHeader.contains("reachable classes"))
      assert(tableHeader.contains("reachable sources"))
      assert(tableHeader.contains("absolute classes"))
      assert(markdown.contains("depth 0"))
      assert(markdown.contains("1 module"))
      assert(markdown.contains("depth 1"))
      assert(markdown.contains("target"))
      assert(markdown.contains("depth 2"))
      assert(markdown.contains("app"))

      val lines = markdown.linesIterator.toSeq
      assert(tableHeader.contains("relationship"))
      val continuousSeparators = lines.filter(line => line.nonEmpty && line.forall(_ == '-'))
      assert(continuousSeparators.size == 2)
      assert(continuousSeparators.forall(_.length > 20))

      val compileDepthLines = lines.dropWhile(!_.startsWith("depth 0"))
      assert(!compileDepthLines.exists(_.contains("--  --")))

      val depthHeaders = lines.filter(line => line.contains("module") && line.contains("relationship"))
      assert(depthHeaders.size == 1)
      assert(depthHeaders.map(_.indexOf("relationship")).distinct.size == 1)

      val domainRow = lines.find(_.contains("domain")).getOrElse("")
      val apiRow = lines.find(_.contains("modules.reallyLong.api")).getOrElse("")
      val domainTokens = domainRow.trim.split("\\s+").toSeq
      val apiTokens = apiRow.trim.split("\\s+").toSeq
      assert(domainRow.indexOf("transitive") == apiRow.indexOf("direct"))
      assert(domainTokens.contains("transitive"))
      assert(apiTokens.contains("direct"))
      assert(domainTokens.count(_ == "2") >= 3)
      assert(apiTokens.count(_ == "2") >= 2)
      assert(domainRow.contains("1 / 2 "))
      assert(apiRow.contains("0 / 2 "))

      val targetRow = lines.find(_.startsWith("target")).getOrElse("")
      val targetDepthRow = lines.find(_.startsWith("depth 2")).getOrElse("")
      val targetTokens = targetRow.trim.split("\\s+").toSeq
      assert(targetRow.contains("app"))
      assert(targetRow.contains("target"))
      assert(targetTokens.contains("3"))
      assert(targetDepthRow.nonEmpty)
      assert(lines.forall(line => !line.endsWith(" ")))
    }

    test("renders common ancestors") {
      val markdown = StrictDepsCommonAncestorsRenderer.render(
        report = StrictDepsCommonAncestorReport(
          rootModuleCount = 2,
          moduleCount = 3,
          commonAncestorCount = 1,
          ancestors = Seq(
            StrictDepsCommonAncestor(
              moduleName = "commonCore",
              neededByModuleCount = 2,
              comparableModuleCount = 2,
              coveragePercent = 100.0,
              compileDepth = 0,
              ownSourceCount = 1,
              directDependencyModuleCount = 0,
              ownSourceLineCount = 20,
              ownClassCount = 2
            ),
            StrictDepsCommonAncestor(
              moduleName = "featureA",
              neededByModuleCount = 0,
              comparableModuleCount = 2,
              coveragePercent = 0.0,
              compileDepth = 1,
              ownSourceCount = 1,
              directDependencyModuleCount = 1,
              ownSourceLineCount = 30,
              ownClassCount = 3
            )
          )
        ),
        limit = 10
      )

      assert(markdown.contains("root modules          2"))
      assert(markdown.contains("modules analyzed      3"))
      assert(markdown.contains("common ancestors      1"))
      assert(markdown.contains("module      needed by  comparable  coverage  depth  own weight  own lines  own classes  direct deps"))
      val commonCoreRow = markdown.linesIterator.find(_.startsWith("commonCore")).getOrElse("")
      assert(commonCoreRow.contains("100.0%"))
      assert(commonCoreRow.endsWith("0"))
      assert(commonCoreRow.contains("20"))
      assert(commonCoreRow.contains("2"))
      assert(markdown.linesIterator.forall(line => !line.endsWith(" ")))
    }

    test("renders compile waste views") {
      val snapshot = StrictDepsCompileWasteSnapshot(
        moduleName = "app",
        dependencySourceCount = 3,
        reachableDependencySourceCount = 2,
        wastedDependencySourceCount = 1,
        dependencyClassCount = 3,
        reachableDependencyClassCount = 2,
        wastedDependencyClassCount = 1,
        deltaSourceCount = 3,
        reachableDeltaSourceCount = 2,
        wastedDeltaSourceCount = 1,
        dependencies = Seq(
          StrictDepsCompileWasteDependency(
            moduleName = "fat",
            declaredDirect = true,
            introducedByModuleNames = Seq("fat"),
            ownSourceCount = 3,
            reachableSourceCount = 2,
            wastedOwnSourceCount = 1,
            reachableSourcePercent = 66.7,
            deltaSourceCount = 3,
            reachableDeltaSourceCount = 2,
            wastedDeltaSourceCount = 1,
            wastedDeltaSourcePercent = 33.3,
            ownClassCount = 3,
            reachableClassCount = 2,
            wastedClassCount = 1,
            reachableClassPercent = 66.7
          )
        )
      )
      val markdown = StrictDepsCompileWasteRenderer.render(snapshot, limit = 10)

      assert(markdown.contains("wasted dependency sources"))
      assert(
        markdown.linesIterator.exists(line =>
          line.startsWith("wasted delta sources") && line.trim.endsWith("1 (33.3%)")
        )
      )
      assert(markdown.contains("module  relationship  introduced by  delta  reachable delta  wasted delta"))
      val fatRow = markdown.linesIterator.find(_.startsWith("fat")).getOrElse("")
      assert(fatRow.contains("direct"))
      assert(fatRow.contains("1 (33.3%)"))
      assert(markdown.linesIterator.forall(line => !line.endsWith(" ")))

      val global = StrictDepsAnalyzer.compileWasteGlobalReport(Seq(snapshot))
      val globalMarkdown = StrictDepsCompileWasteRenderer.renderGlobal(global, limit = 10)

      assert(globalMarkdown.contains("root modules"))
      assert(globalMarkdown.contains("bad nodes"))
      assert(globalMarkdown.contains("bad edges"))
      val edgeRow = globalMarkdown.linesIterator.find(line =>
        line.startsWith("app") && line.contains("fat")
      ).getOrElse("")
      assert(edgeRow.contains("direct"))
      assert(edgeRow.contains("fat"))
      assert(globalMarkdown.linesIterator.forall(line => !line.endsWith(" ")))
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
