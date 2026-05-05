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
        assert(json("unusedDirectModuleDeps").arr.exists(_.str == "server"))
        assert(
          json("missingDirectModuleDeps").arr.exists { usage =>
            usage("moduleName").str == "domain"
          }
        )
        assert(
          json("usedDirectModuleDeps").arr.exists { usage =>
            usage("moduleName").str == "helper" &&
            usage("usedClasses").arr.exists(_.str == "com.example.helper.Helper")
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
  }
}
