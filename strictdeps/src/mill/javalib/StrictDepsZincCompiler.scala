package mill.javalib

import mill.*
import mill.javalib.api.CompilationResult
import mill.javalib.api.JvmWorkerUtil
import mill.javalib.api.internal.JavaCompilerOptions
import mill.javalib.api.internal.ZincOp
import mill.scalalib.ScalaModule

object StrictDepsZincCompiler {

  final class Prepared(private val regenerate0: os.Path => CompilationResult) {
    def regenerate(workDir: os.Path): CompilationResult = regenerate0(workDir)
  }

  def prepare(module: JavaModule): Task[Prepared] = {
    module match {
      case scalaModule: ScalaModule => prepareScala(scalaModule)
      case javaModule => prepareJava(javaModule)
    }
  }

  private def prepareScala(module: ScalaModule): Task[Prepared] = Task.Anon {
    val scalaVersion = module.scalaVersion()
    val javacOptions = JavaCompilerOptions.split(
      module.javacOptions() ++ module.mandatoryJavacOptions()
    )
    val worker = module.jvmWorker().internalWorker()
    val upstreamCompileOutput = module.upstreamCompileOutput()
    val sources = module.allSourceFiles().map(_.path)
    val compileClasspath = module.compileClasspath()
    val scalaOrganization = JvmWorkerUtil.scalaOrganization(scalaVersion)
    val scalacOptions = module.allScalacOptions() ++ module.tastyReproducibilityScalacOptions()
    val compilerClasspath = module.scalaCompilerClasspath()
    val scalacPluginClasspath = module.scalacPluginClasspath()
    val compilerBridge = module.scalaCompilerBridge()
    val incrementalCompilation = module.zincIncrementalCompilation()
    val auxiliaryClassFileExtensions = module.zincAuxiliaryClassFileExtensions()
    val javaHome = module.javaHome().map(_.path)
    val reporter = Task.reporter.apply(module.hashCode)
    val reportCachedProblems = module.zincReportCachedProblems()

    Prepared { workDir =>
      worker
        .apply(
          ZincOp.CompileMixed(
            upstreamCompileOutput = upstreamCompileOutput,
            sources = sources,
            compileClasspath = compileClasspath,
            javacOptions = javacOptions.compiler,
            scalaVersion = scalaVersion,
            scalaOrganization = scalaOrganization,
            scalacOptions = scalacOptions,
            compilerClasspath = compilerClasspath,
            scalacPluginClasspath = scalacPluginClasspath,
            compilerBridgeOpt = compilerBridge,
            incrementalCompilation = incrementalCompilation,
            auxiliaryClassFileExtensions = auxiliaryClassFileExtensions,
            workDir = workDir
          ),
          javaHome = javaHome,
          javaRuntimeOptions = javacOptions.runtime,
          reporter = reporter,
          reportCachedProblems = reportCachedProblems
        )
        .get
    }
  }

  private def prepareJava(module: JavaModule): Task[Prepared] = Task.Anon {
    val (javacOptions, legacyRuntimeOptions) = JavaModule.splitJavacAndRuntimeOptions(
      module.javacOptions() ++
        module.mandatoryJavacOptions() ++
        module.annotationProcessorsJavacOptions()
    )
    val worker = module.jvmWorker().internalWorker()
    val upstreamCompileOutput = module.upstreamCompileOutput()
    val sources = module.allSourceFiles().map(_.path)
    val compileClasspath = module.compileClasspath()
    val incrementalCompilation = module.zincIncrementalCompilation()
    val javaHome = module.javaHome().map(_.path)
    val javaRuntimeOptions = module.javaCompilerRuntimeOptions() ++ legacyRuntimeOptions
    val reporter = Task.reporter.apply(module.hashCode)
    val reportCachedProblems = module.zincReportCachedProblems()

    Prepared { workDir =>
      val generatedSources = workDir / "generated-sources"
      os.remove.all(generatedSources)
      os.makeDir.all(generatedSources)
      worker
        .apply(
          ZincOp.CompileJava(
            upstreamCompileOutput = upstreamCompileOutput,
            sources = sources,
            compileClasspath = compileClasspath,
            javacOptions = Seq("-s", generatedSources.toString) ++ javacOptions,
            incrementalCompilation = incrementalCompilation,
            workDir = workDir
          ),
          javaHome = javaHome,
          javaRuntimeOptions = javaRuntimeOptions,
          reporter = reporter,
          reportCachedProblems = reportCachedProblems
        )
        .get
    }
  }
}
