package io.github.nguyenyou.millstrictdeps

import mill.*
import mill.api.Discover
import mill.api.daemon.ExecResult
import mill.scalalib.ScalaModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester
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

  lazy val millDiscover = Discover[this.type]
}

object StrictDepsModuleTests extends TestSuite {
  def tests: Tests = Tests {
    test("reports unused and missing direct module deps") {
      val resourceFolder = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
      UnitTester(StrictDepsFixtureBuild, resourceFolder / "strict-deps-project").scoped { eval =>
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

        val fixPlanResult = eval(StrictDepsFixtureBuild.app.strictDepsFixPlan).fold(
          failure => throw new Exception(failure.toString),
          identity
        )
        val fixPlan = os.read(fixPlanResult.value.path)

        assert(fixPlan.contains("Add `domain`"))
        assert(fixPlan.contains("Remove `server`"))
        assert(fixPlan.contains("does not mutate `build.mill`"))
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
  }
}
