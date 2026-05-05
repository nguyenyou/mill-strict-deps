package io.github.nguyenyou.millstrictdeps

import mill.*
import mill.api.Discover
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
  }
}
