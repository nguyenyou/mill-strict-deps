package io.github.nguyenyou.millstrictdeps

import mill.*
import mill.api.Discover
import mill.scalalib.ScalaModule
import mill.testkit.TestRootModule
import mill.testkit.UnitTester
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
      }
    }
  }
}
