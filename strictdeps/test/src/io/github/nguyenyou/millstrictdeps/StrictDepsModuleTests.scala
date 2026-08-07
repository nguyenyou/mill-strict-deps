package io.github.nguyenyou.millstrictdeps

import mill.*
import mill.api.PathRef
import mill.api.Discover
import mill.api.daemon.ExecResult
import mill.scalalib.ScalaModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester
import mill.util.Tasks
import ujson.read
import utest.*

object StrictDepsFixtureBuild extends TestRootModule {
  def sharedScalaVersion = sys.props("mill.strictdeps.test.scalaVersion")

  object domain extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object api extends ScalaModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(domain)
  }

  object server extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object helper extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object app extends ScalaModule with StrictDepsModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(api, server, helper)
  }

  object uiWidget extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object appB extends ScalaModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(uiWidget)
  }

  object appA extends ScalaModule with StrictDepsModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(appB)
  }

  object fat extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object reachClient extends ScalaModule with StrictDepsModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(fat)
  }

  object componentLib extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object componentClient extends ScalaModule with StrictDepsModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(componentLib)
  }

  object commonCore extends ScalaModule {
    def scalaVersion = sharedScalaVersion
  }

  object commonA extends ScalaModule with StrictDepsModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(commonCore)
  }

  object commonB extends ScalaModule with StrictDepsModule {
    def scalaVersion = sharedScalaVersion
    override def moduleDeps = Seq(commonCore)
  }

  lazy val millDiscover = Discover[this.type]
}

object StrictDepsModuleTests extends TestSuite {
  def tests: Tests = Tests {
    test("reports unused and missing direct module deps") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        val moduleSourceFile = PathRef.toResolvedOsPath(os.Path(sourcecode.File(), os.pwd))
        os.copy.over(
          moduleSourceFile,
          eval.outPath / os.up / os.up / "mill-workspace" /
            "strictdeps/test/src/io/github/nguyenyou/millstrictdeps/StrictDepsModuleTests.scala",
          createFolders = true
        )
        val result = eval(StrictDepsFixtureBuild.app.strictDepsReport).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val markdown = os.read(result.value.path)

        assert(markdown.contains("Unused Direct Module Deps"))
        assert(markdown.contains("server"))
        assert(markdown.contains("Missing Direct Module Deps"))
        assert(markdown.contains("domain"))
        assert(markdown.contains("Used Direct Module Deps"))
        assert(markdown.contains("api"))
        assert(markdown.contains("helper"))
        assert(markdown.contains("com.example.helper.Helper"))

        val jsonResult = eval(StrictDepsFixtureBuild.app.strictDepsJsonReport).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val json = read(os.read(jsonResult.value.path))

        assert(json("moduleName").str == "app")
        assert(json("hasProblems").bool)
        assert(json("summary")("usedDirectModuleDeps").num == 2)
        assert(!json("unusedDirectModuleDeps").arr.exists(_.str == "api"))
        assert(json("unusedDirectModuleDeps").arr.exists(_.str == "server"))
        assert(
          json("missingDirectModuleDeps").arr.exists { usage =>
            usage("moduleName").str == "domain"
          }
        )
        assert(
          json("usedDirectModuleDeps").arr.exists { usage =>
            usage("moduleName").str == "api" &&
            usage("usedClasses").arr.exists(_.str == "com.example.api.Api")
          }
        )
        assert(
          json("usedDirectModuleDeps").arr.exists { usage =>
            usage("moduleName").str == "helper" &&
            usage("usedClasses").arr.exists(_.str == "com.example.helper.Helper") &&
            usage("usedClasses").arr.exists(_.str == "com.example.helper.ScalaHelper")
          }
        )
        assert(
          json("dependencyUsageWeights").arr.exists { weight =>
            weight("moduleName").str == "helper" &&
            weight("declaredDirect").bool &&
            weight("usedClassCount").num > 0 &&
            weight("currentModuleUsagePercent").num > 0 &&
            weight("dependencyTouchedPercent").num > 0
          }
        )
        assert(
          json("dependencyUsageWeights").arr.exists { weight =>
            weight("moduleName").str == "domain" &&
            !weight("declaredDirect").bool &&
            weight("usedClasses").arr.exists(_.str == "com.example.domain.User")
          }
        )
        val dependencyWeights = json("dependencyWeights").arr
        val apiWeight = dependencyWeights.find { weight =>
          weight("moduleName").str == "api"
        }.getOrElse(throw new Exception("api dependency weight not found"))
        val domainWeight = dependencyWeights.find { weight =>
          weight("moduleName").str == "domain"
        }.getOrElse(throw new Exception("domain dependency weight not found"))

        assert(json("summary")("dependencyWeightModules").num == 4)
        assert(apiWeight("declaredDirect").bool)
        assert(apiWeight("directDependencyModuleNames").arr.exists(_.str == "domain"))
        assert(apiWeight("transitiveDependencyModuleNames").arr.exists(_.str == "domain"))
        assert(apiWeight("ownSourceCount").num == 1)
        assert(apiWeight("absoluteSourceCount").num == 2)
        assert(apiWeight("deltaSourceCount").num == 2)
        assert(apiWeight("ownSourceLineCount").num > 0)
        assert(apiWeight("absoluteSourceLineCount").num > apiWeight("ownSourceLineCount").num)
        assert(apiWeight("deltaSourceLineCount").num == apiWeight("absoluteSourceLineCount").num)
        assert(apiWeight("ownClassCount").num == 1)
        assert(apiWeight("absoluteClassCount").num == 2)
        assert(apiWeight("deltaKind").str == "remove")
        assert(!domainWeight("declaredDirect").bool)
        assert(domainWeight("ownSourceCount").num == 1)
        assert(domainWeight("absoluteSourceCount").num == 1)
        assert(domainWeight("deltaSourceCount").num == 0)
        assert(domainWeight("ownSourceLineCount").num > 0)
        assert(domainWeight("absoluteSourceLineCount").num == domainWeight("ownSourceLineCount").num)
        assert(domainWeight("deltaSourceLineCount").num == 0)
        assert(domainWeight("ownClassCount").num == 1)
        assert(domainWeight("absoluteClassCount").num == 1)
        assert(domainWeight("deltaKind").str == "add")

        val weightReport = json("weightReport")
        val compileWaste = json("compileWaste")
        val serverWaste = compileWaste("dependencies").arr.find { dependency =>
          dependency("moduleName").str == "server"
        }.getOrElse(throw new Exception("server compile waste dependency not found"))

        assert(weightReport("dependencySources")("zincSourceCount").num == 5)
        assert(weightReport("totalSources")("zincSourceCount").num == 7)
        assert(weightReport("dependencyClassCount").num == 5)
        assert(weightReport("totalClassCount").num == 7)
        assert(weightReport("dependencyWeights").arr.exists { weight =>
          weight("moduleName").str == "api" &&
          weight("deltaSources")("zincSourceCount").num == 2 &&
          weight("introducedByModuleNames").arr.exists(_.str == "api")
        })
        assert(compileWaste("moduleName").str == "app")
        assert(compileWaste("deltaSourceCount").num == 5)
        assert(compileWaste("wastedDeltaSourceCount").num == 1)
        assert(serverWaste("declaredDirect").bool)
        assert(serverWaste("relationship").str == "direct")
        assert(serverWaste("usedClassCount").num == 0)
        assert(serverWaste("usedClassTotalCount").num == 1)
        assert(serverWaste("wastedDeltaSourceCount").num == 1)

        val fixPlanResult = eval(StrictDepsFixtureBuild.app.strictDepsFixPlan).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val fixPlan = os.read(fixPlanResult.value.path)

        assert(fixPlan.contains("Add `domain`"))
        assert(fixPlan.contains("absolute weight: 1 source, delta weight: 0 sources add"))
        assert(fixPlan.contains("Remove `server`"))
        assert(fixPlan.contains("absolute weight: 1 source, delta weight: 1 source remove"))
        assert(fixPlan.contains("does not mutate `build.mill`"))

        val autofixPlanResult = eval(StrictDepsFixtureBuild.app.strictDepsAutofixPlan).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val autofixPlan = os.read(autofixPlanResult.value.path)

        assert(autofixPlan.contains("add `domain` in `moduleDeps` as `domain`"))
        assert(autofixPlan.contains("remove `server` in `moduleDeps`"))
        assert(autofixPlan.contains("## Refused"))
        assert(autofixPlan.contains("_None._"))

        eval(StrictDepsFixtureBuild.app.strictDepsApplyFix(dryRun = true)) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsApplyFix dry run failure: $failure")
          case Right(_) =>
            ()
        }
      }
    }

    test("regenerates and materializes Zinc analysis when compile cache omitted it") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        def force[T](result: Either[?, UnitTester.Result[T]]): T = {
          result.fold(
            failure => throw new Exception(failure.toString),
            success => success.value
          )
        }

        val compilations = Seq(
          force(eval(StrictDepsFixtureBuild.app.compile)),
          force(eval(StrictDepsFixtureBuild.api.compile)),
          force(eval(StrictDepsFixtureBuild.domain.compile)),
          force(eval(StrictDepsFixtureBuild.helper.compile)),
          force(eval(StrictDepsFixtureBuild.server.compile))
        )
        compilations.map(_.analysisFile).filter(os.exists).foreach(os.remove)

        val report = force(eval(StrictDepsFixtureBuild.app.strictDepsReport))
        val markdown = os.read(report.path)

        assert(markdown.contains("Unused Direct Module Deps"))
        assert(markdown.contains("server"))
        assert(markdown.contains("Missing Direct Module Deps"))
        assert(markdown.contains("domain"))
        assert(markdown.contains("Used Direct Module Deps"))
        assert(markdown.contains("api"))
        assert(markdown.contains("helper"))

        val materializedAnalysis = force(eval(StrictDepsFixtureBuild.app.strictDepsZincAnalysis))
        assert(materializedAnalysis.validate())
        assert(StrictDepsZincFile.containsAnalysis(materializedAnalysis.path))
      }
    }

    test("fails when appA depends on appB only to use uiWidget symbols") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        val jsonResult = eval(StrictDepsFixtureBuild.appA.strictDepsJsonReport).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val json = read(os.read(jsonResult.value.path))

        assert(json("moduleName").str == "appA")
        assert(json("hasProblems").bool)
        assert(json("unusedDirectModuleDeps").arr.exists(_.str == "appB"))
        assert(
          json("missingDirectModuleDeps").arr.exists { usage =>
            usage("moduleName").str == "uiWidget"
          }
        )
        assert(
          json("dependencyUsageWeights").arr.exists { weight =>
            weight("moduleName").str == "uiWidget" &&
            !weight("declaredDirect").bool &&
            weight("usedClassCount").num > 0
          }
        )
        val dependencyWeights = json("dependencyWeights").arr
        val appBWeight = dependencyWeights.find { weight =>
          weight("moduleName").str == "appB"
        }.getOrElse(throw new Exception("appB dependency weight not found"))
        val uiWidgetWeight = dependencyWeights.find { weight =>
          weight("moduleName").str == "uiWidget"
        }.getOrElse(throw new Exception("uiWidget dependency weight not found"))

        assert(appBWeight("declaredDirect").bool)
        assert(appBWeight("absoluteSourceCount").num == 2)
        assert(appBWeight("deltaSourceCount").num == 2)
        assert(appBWeight("deltaKind").str == "remove")
        assert(appBWeight("transitiveDependencyModuleNames").arr.exists(_.str == "uiWidget"))
        assert(!uiWidgetWeight("declaredDirect").bool)
        assert(uiWidgetWeight("absoluteSourceCount").num == 1)
        assert(uiWidgetWeight("deltaSourceCount").num == 0)
        assert(uiWidgetWeight("deltaKind").str == "add")

        eval(StrictDepsFixtureBuild.appA.strictDepsCheck()) match {
          case Left(failure: ExecResult.Failure[?]) =>
            assert(failure.msg.contains("unused direct module deps: appB"))
            assert(failure.msg.contains("missing direct module deps: uiWidget"))
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCheck failure: $failure")
          case Right(_) =>
            throw new Exception("strictDepsCheck unexpectedly passed")
        }
      }
    }

    test("reports dependency class and source reachability") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        val jsonResult = eval(StrictDepsFixtureBuild.reachClient.strictDepsJsonReport).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val json = read(os.read(jsonResult.value.path))
        val reachability = json("reachability")
        val fatModule = reachability("modules").arr.find { module =>
          module("moduleName").str == "fat"
        }.getOrElse(throw new Exception("fat reachability module not found"))

        assert(json("moduleName").str == "reachClient")
        assert(!json("hasProblems").bool)
        assert(reachability("providedClassCount").num == 3)
        assert(reachability("directUsedClassCount").num == 1)
        assert(reachability("reachableClassCount").num == 2)
        assert(reachability("unusedClassCount").num == 1)
        assert(reachability("providedSourceCount").num == 3)
        assert(reachability("reachableSourceCount").num == 2)
        assert(reachability("unusedSourceCount").num == 1)
        assert(json("dependencyWeights")(0)("moduleName").str == "fat")
        assert(json("dependencyWeights")(0)("ownSourceCount").num == 3)
        assert(json("dependencyWeights")(0)("absoluteSourceCount").num == 3)
        assert(json("dependencyWeights")(0)("deltaSourceCount").num == 3)
        assert(json("dependencyWeights")(0)("deltaKind").str == "remove")
        assert(json("weightReport")("dependencyWeights")(0)("moduleName").str == "fat")
        assert(json("weightReport")("dependencyWeights")(0)("deltaSources")("zincSourceCount").num == 3)
        assert(json("weightReport")("dependencyWeights")(0)("reachableDeltaSourceCount").num == 2)
        assert(json("weightReport")("dependencyWeights")(0)("wastedDeltaSourceCount").num == 1)
        assert(json("compileWaste")("dependencies")(0)("moduleName").str == "fat")
        assert(json("compileWaste")("dependencies")(0)("deltaSourceCount").num == 3)
        assert(json("compileWaste")("dependencies")(0)("reachableDeltaSourceCount").num == 2)
        assert(json("compileWaste")("dependencies")(0)("wastedDeltaSourceCount").num == 1)
        assert(fatModule("directUsedClasses").arr.exists(_.str == "com.example.fat.FatEntry"))
        assert(fatModule("reachableClasses").arr.exists(_.str == "com.example.fat.Needed"))
        assert(fatModule("unusedClasses").arr.exists(_.str == "com.example.fat.Unused"))
        assert(fatModule("unusedSources").arr.exists(_.str.endsWith("Unused.scala")))
        assert(fatModule("reachableClassPercent").num == 66.7)
        assert(fatModule("reachableSourcePercent").num == 66.7)

        val markdownResult = eval(StrictDepsFixtureBuild.reachClient.strictDepsReport).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val markdown = os.read(markdownResult.value.path)

        assert(markdown.contains("Classpath Reachability"))
        assert(markdown.contains("com.example.fat.FatEntry"))
        assert(markdown.contains("Unused.scala"))
      }
    }

    test("marks companion-created helper classes as reachable") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        val jsonResult = eval(StrictDepsFixtureBuild.componentClient.strictDepsJsonReport).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val json = read(os.read(jsonResult.value.path))
        val componentLib = json("reachability")("modules").arr.find { module =>
          module("moduleName").str == "componentLib"
        }.getOrElse(throw new Exception("componentLib reachability module not found"))

        val reachableClasses = componentLib("reachableClasses").arr.map(_.str).toSet
        val unusedClasses = componentLib("unusedClasses").arr.map(_.str).toSet
        val reachableSources = componentLib("reachableSources").arr.map(_.str)
        val unusedSources = componentLib("unusedSources").arr.map(_.str)

        assert(json("moduleName").str == "componentClient")
        assert(!json("hasProblems").bool)
        assert(componentLib("directUsedClasses").arr.exists(_.str == "com.example.component.Facade"))
        assert(reachableClasses.contains("com.example.component.Facade"))
        assert(reachableClasses.contains("com.example.component.Facade$.Backend"))
        assert(reachableClasses.contains("com.example.component.ChildWrapper"))
        assert(!unusedClasses.contains("com.example.component.Facade$.Backend"))
        assert(!unusedClasses.contains("com.example.component.ChildWrapper"))
        assert(reachableSources.exists(_.endsWith("Facade.scala")))
        assert(reachableSources.exists(_.endsWith("ChildWrapper.scala")))
        assert(unusedSources.exists(_.endsWith("UnusedComponent.scala")))
      }
    }

    test("runs strictDepsWeight command") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        eval(StrictDepsFixtureBuild.app.strictDepsWeight()) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsWeight failure: $failure")
          case Right(_) =>
            ()
        }

        def force[T](result: Either[?, UnitTester.Result[T]]): T = {
          result.fold(
            failure => throw new Exception(failure.toString),
            success => success.value
          )
        }
        def sourceFiles(files: Seq[PathRef]): Seq[String] = {
          files.map(_.path.toString).distinct.sorted
        }

        val appCompile = force(eval(StrictDepsFixtureBuild.app.compile))
        val apiCompile = force(eval(StrictDepsFixtureBuild.api.compile))
        val domainCompile = force(eval(StrictDepsFixtureBuild.domain.compile))
        val helperCompile = force(eval(StrictDepsFixtureBuild.helper.compile))
        val serverCompile = force(eval(StrictDepsFixtureBuild.server.compile))
        val apiSources = sourceFiles(force(eval(StrictDepsFixtureBuild.api.allSourceFiles)))
        val domainSources = sourceFiles(force(eval(StrictDepsFixtureBuild.domain.allSourceFiles)))
        val helperSources = sourceFiles(force(eval(StrictDepsFixtureBuild.helper.allSourceFiles)))
        val serverSources = sourceFiles(force(eval(StrictDepsFixtureBuild.server.allSourceFiles)))

        val report = StrictDepsAnalyzer.weightReport(
          currentAnalysisFile = PathRef(appCompile.analysisFile),
          currentModuleSourceFiles = sourceFiles(force(eval(StrictDepsFixtureBuild.app.allSourceFiles))).toSet,
          directModuleNames = Set("api", "helper", "server"),
          millTransitiveModules = Seq(
            StrictDepsModuleWeightSnapshot("api", apiSources, Seq("domain")),
            StrictDepsModuleWeightSnapshot("domain", domainSources),
            StrictDepsModuleWeightSnapshot("helper", helperSources),
            StrictDepsModuleWeightSnapshot("server", serverSources)
          ),
          zincTransitiveModules = Seq(
            StrictDepsModuleSnapshot("api", PathRef(apiCompile.analysisFile), Seq("domain")),
            StrictDepsModuleSnapshot("domain", PathRef(domainCompile.analysisFile)),
            StrictDepsModuleSnapshot("helper", PathRef(helperCompile.analysisFile)),
            StrictDepsModuleSnapshot("server", PathRef(serverCompile.analysisFile))
          ),
          ignoredModuleNames = Set.empty
        )
        val weightsByModule = report.dependencyWeights.map(weight => weight.moduleName -> weight).toMap
        val deltaSourceCounts = weightsByModule.view.mapValues(_.deltaSources.millSourceCount).toMap
        val compileDepthDeltaSourceCounts =
          weightsByModule.view.mapValues(_.compileDepthDeltaSources.millSourceCount).toMap

        assert(report.dependencySources.millSourceCount == 5)
        assert(report.dependencySourceLines.millSourceCount > report.dependencySources.millSourceCount)
        assert(report.currentModuleClassCount == 2)
        assert(report.dependencyClassCount == 5)
        assert(report.totalClassCount == 7)
        assert(deltaSourceCounts == Map("api" -> 2, "helper" -> 2, "server" -> 1, "domain" -> 0))
        assert(deltaSourceCounts.values.sum == report.dependencySources.millSourceCount)
        assert(weightsByModule("api").ownSourceLines.millSourceCount > 0)
        assert(weightsByModule("api").absoluteSourceLines.millSourceCount > weightsByModule("api").ownSourceLines.millSourceCount)
        assert(weightsByModule("api").deltaSourceLines.millSourceCount == weightsByModule("api").absoluteSourceLines.millSourceCount)
        assert(weightsByModule("api").ownClassCount == 1)
        assert(weightsByModule("api").absoluteClassCount == 2)
        assert(weightsByModule("api").usedClassCount == 1)
        assert(weightsByModule("api").usedClassTotalCount == 1)
        assert(weightsByModule("api").usedClassPercent == 100.0)
        assert(weightsByModule("api").reachableClassCount == 1)
        assert(weightsByModule("api").reachableClassTotalCount == 1)
        assert(weightsByModule("api").reachableClassPercent == 100.0)
        assert(weightsByModule("api").reachableSourceCount == 1)
        assert(weightsByModule("api").reachableSourceTotalCount == 1)
        assert(weightsByModule("api").reachableSourcePercent == 100.0)
        assert(weightsByModule("api").introducedByModuleNames == Seq("api"))
        assert(weightsByModule("api").reachableDeltaSourceCount == 2)
        assert(weightsByModule("api").wastedDeltaSourceCount == 0)
        assert(weightsByModule("domain").introducedByModuleNames == Seq("api"))
        assert(weightsByModule("server").usedClassCount == 0)
        assert(weightsByModule("server").usedClassPercent == 0.0)
        assert(weightsByModule("server").reachableClassCount == 0)
        assert(weightsByModule("server").reachableSourceCount == 0)
        assert(weightsByModule("server").introducedByModuleNames == Seq("server"))
        assert(weightsByModule("server").reachableDeltaSourceCount == 0)
        assert(weightsByModule("server").wastedDeltaSourceCount == 1)
        assert(weightsByModule("server").wastedOwnSourceCount == 1)
        assert(weightsByModule("server").wastedClassCount == 1)
        assert(compileDepthDeltaSourceCounts == Map("api" -> 1, "helper" -> 2, "server" -> 1, "domain" -> 1))
        assert(compileDepthDeltaSourceCounts.values.sum == report.dependencySources.millSourceCount)
        assert(weightsByModule("api").compileDepthDeltaSourceLines.millSourceCount == weightsByModule("api").ownSourceLines.millSourceCount)
        assert(report.reachability.providedClassCount == 5)
        assert(report.reachability.directUsedClassCount == 4)
        assert(report.reachability.reachableClassCount == 4)
        assert(report.reachability.unusedClassCount == 1)
        assert(report.reachability.reachableClassPercent == 80.0)
        assert(report.reachability.providedSourceCount == 5)
        assert(report.reachability.directUsedSourceCount == 4)
        assert(report.reachability.reachableSourceCount == 4)
        assert(report.reachability.unusedSourceCount == 1)
        assert(report.reachability.reachableSourcePercent == 80.0)
      }
    }

    test("runs strictDepsCompileDepth command") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        eval(StrictDepsFixtureBuild.app.strictDepsCompileDepth()) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCompileDepth failure: $failure")
          case Right(_) =>
            ()
        }
        eval(StrictDepsFixtureBuild.app.strictDepsCompileDepth(zeroReachableSourcesOnly = true)) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCompileDepth zero-reachable filter failure: $failure")
          case Right(_) =>
            ()
        }
        eval(StrictDepsFixtureBuild.app.strictDepsCompileDepth(showSummary = false)) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCompileDepth summary filter failure: $failure")
          case Right(_) =>
            ()
        }
        eval(StrictDepsFixtureBuild.app.strictDepsCompileDepth(showSummary = true)) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCompileDepth summary opt-in failure: $failure")
          case Right(_) =>
            ()
        }
      }
    }

    test("runs strictDepsWhoIntroduces command") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        eval(StrictDepsFixtureBuild.app.strictDepsWhoIntroduces("domain")) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsWhoIntroduces failure: $failure")
          case Right(_) =>
            ()
        }
      }
    }

    test("runs strictDepsCompileWaste command and global aggregation") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        def force[T](result: Either[?, UnitTester.Result[T]]): T = {
          result.fold(
            failure => throw new Exception(failure.toString),
            success => success.value
          )
        }

        eval(StrictDepsFixtureBuild.reachClient.strictDepsCompileWaste()) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCompileWaste failure: $failure")
          case Right(_) =>
            ()
        }

        val appSnapshot = force(eval(StrictDepsFixtureBuild.app.strictDepsCompileWasteSnapshot))
        val reachSnapshot = force(eval(StrictDepsFixtureBuild.reachClient.strictDepsCompileWasteSnapshot))
        val server = appSnapshot.dependencies.find(_.moduleName == "server").getOrElse {
          throw new Exception("server compile waste row not found")
        }
        val fat = reachSnapshot.dependencies.find(_.moduleName == "fat").getOrElse {
          throw new Exception("fat compile waste row not found")
        }

        assert(appSnapshot.wastedDeltaSourceCount == 1)
        assert(server.wastedDeltaSourceCount == 1)
        assert(server.wastedOwnSourceCount == 1)
        assert(server.introducedByModuleNames == Seq("server"))
        assert(reachSnapshot.wastedDeltaSourceCount == 1)
        assert(fat.deltaSourceCount == 3)
        assert(fat.reachableDeltaSourceCount == 2)
        assert(fat.wastedDeltaSourceCount == 1)
        assert(fat.usedClassCount == 1)
        assert(fat.usedClassTotalCount == 3)

        val global = StrictDepsAnalyzer.compileWasteGlobalReport(Seq(appSnapshot, reachSnapshot))
        val fatNode = global.badNodes.find(_.moduleName == "fat").getOrElse {
          throw new Exception("fat compile waste node not found")
        }
        val fatEdge = global.badEdges.find { edge =>
          edge.moduleName == "reachClient" && edge.dependencyModuleName == "fat"
        }.getOrElse(throw new Exception("reachClient -> fat compile waste edge not found"))

        assert(global.rootModuleCount == 2)
        assert(global.totalWastedDeltaSourceCount == 2)
        assert(fatNode.totalWastedDeltaSourceCount == 1)
        assert(fatNode.totalDeltaSourceCount == 3)
        assert(fatEdge.wastedDeltaSourceCount == 1)

        val downstream = StrictDepsAnalyzer.downstreamUsageReport(
          targetModuleName = "fat",
          snapshots = Seq(appSnapshot, reachSnapshot)
        )
        val fatClient = downstream.downstreamModules.find(_.moduleName == "reachClient").getOrElse {
          throw new Exception("reachClient downstream usage row not found")
        }

        assert(downstream.rootModuleCount == 2)
        assert(downstream.downstreamModuleCount == 1)
        assert(downstream.directDownstreamModuleCount == 1)
        assert(fatClient.relationship == "direct")
        assert(fatClient.usedClassCount == 1)
        assert(fatClient.reachableClassCount == 2)
        assert(fatClient.reachableSourceCount == 2)

        eval(
          strictDepsCompileWaste.compileWaste(
            Tasks(Seq(
              StrictDepsFixtureBuild.app.strictDepsCompileWasteSnapshot,
              StrictDepsFixtureBuild.reachClient.strictDepsCompileWasteSnapshot
            )),
            limit = 10
          )
        ) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCompileWaste global failure: $failure")
          case Right(_) =>
            ()
        }

        eval(
          strictDepsDownstreamUsage.downstreamUsage(
            target = "fat",
            snapshots = Tasks(Seq(
              StrictDepsFixtureBuild.app.strictDepsCompileWasteSnapshot,
              StrictDepsFixtureBuild.reachClient.strictDepsCompileWasteSnapshot
            )),
            limit = 10
          )
        ) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsDownstreamUsage global failure: $failure")
          case Right(_) =>
            ()
        }
      }
    }

    test("runs strictDepsCommonAncestors global command") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
        def force[T](result: Either[?, UnitTester.Result[T]]): T = {
          result.fold(
            failure => throw new Exception(failure.toString),
            success => success.value
          )
        }

        val commonASnapshot = force(eval(StrictDepsFixtureBuild.commonA.strictDepsGraphSnapshot))
        val commonBSnapshot = force(eval(StrictDepsFixtureBuild.commonB.strictDepsGraphSnapshot))
        val report = StrictDepsAnalyzer.commonAncestorReport(
          snapshots = Seq(commonASnapshot, commonBSnapshot)
        )
        val commonCore = report.ancestors.find(_.moduleName == "commonCore").getOrElse {
          throw new Exception("commonCore common ancestor not found")
        }

        assert(report.rootModuleCount == 2)
        assert(report.moduleCount == 3)
        assert(report.commonAncestorCount == 1)
        assert(commonCore.isCommonAncestor)
        assert(commonCore.neededByModuleCount == 2)
        assert(commonCore.comparableModuleCount == 2)
        assert(commonCore.coveragePercent == 100.0)
        assert(commonCore.ownSourceLineCount > 0)
        assert(commonCore.ownClassCount == 1)

        eval(
          strictDepsCommonAncestors.commonAncestors(
            Tasks(Seq(
              StrictDepsFixtureBuild.commonA.strictDepsGraphSnapshot,
              StrictDepsFixtureBuild.commonB.strictDepsGraphSnapshot
            )),
            limit = 10
          )
        ) match {
          case Left(failure) =>
            throw new Exception(s"Unexpected strictDepsCommonAncestors failure: $failure")
          case Right(_) =>
            ()
        }
      }
    }
  }
}
