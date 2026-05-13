package io.github.nguyenyou.millstrictdeps

import mill.*
import mill.api.Result
import mill.javalib.JavaModule
import mill.scalalib.ScalaModule

trait StrictDepsModule extends ScalaModule { outer =>

  /** Module names to ignore in strict-deps reports and checks. */
  def strictDepsIgnoredModuleDeps: T[Seq[String]] = Task {
    Seq.empty[String]
  }

  /** Maximum class names to show per module in markdown output. */
  def strictDepsMaxClassesPerModule: T[Int] = Task {
    12
  }

  /** Whether `strictDepsCheck` fails when direct module deps are unused. */
  def strictDepsFailOnUnusedDirectModuleDeps: T[Boolean] = Task {
    true
  }

  /** Whether `strictDepsCheck` fails when transitive module deps are used directly. */
  def strictDepsFailOnMissingDirectModuleDeps: T[Boolean] = Task {
    true
  }

  def strictDepsReport: T[PathRef] = Task {
    val report = analyzeStrictDeps()()
    val out = Task.dest / "strict-deps-report.md"
    os.write.over(
      out,
      StrictDepsMarkdownRenderer.render(
        moduleName = moduleSegments.render,
        report = report,
        maxClassesPerModule = strictDepsMaxClassesPerModule()
      )
    )
    Task.log.info(s"strictDepsReport -> $out")
    PathRef(out)
  }

  def strictDepsJsonReport: T[PathRef] = Task {
    val report = analyzeStrictDeps()()
    val weightReport = analyzeStrictDepsWeight()()
    val compileWaste = StrictDepsAnalyzer.compileWasteSnapshot(
      moduleName = moduleSegments.render,
      report = weightReport
    )
    val out = Task.dest / "strict-deps-report.json"
    os.write.over(
      out,
      StrictDepsJsonRenderer.render(
        moduleName = moduleSegments.render,
        report = report,
        weightReport = Some(weightReport),
        compileWaste = Some(compileWaste)
      )
    )
    Task.log.info(s"strictDepsJsonReport -> $out")
    PathRef(out)
  }

  def strictDepsFixPlan: T[PathRef] = Task {
    val report = analyzeStrictDeps()()
    val out = Task.dest / "strict-deps-fix-plan.md"
    os.write.over(
      out,
      StrictDepsFixPlanRenderer.render(
        moduleName = moduleSegments.render,
        report = report,
        maxClassesPerModule = strictDepsMaxClassesPerModule()
      )
    )
    Task.log.info(s"strictDepsFixPlan -> $out")
    PathRef(out)
  }

  def strictDepsWeight(): Command[Unit] = Task.Command {
    val report = analyzeStrictDepsWeight()()
    Task.log.info(
      "\n" + StrictDepsWeightRenderer.render(
        moduleName = moduleSegments.render,
        report = report
      )
    )
    Result.Success(())
  }

  def strictDepsCompileDepth(): Command[Unit] = Task.Command {
    val report = analyzeStrictDepsWeight()()
    Task.log.info(
      "\n" + StrictDepsCompileDepthRenderer.render(
        moduleName = moduleSegments.render,
        report = report
      )
    )
    Result.Success(())
  }

  def strictDepsCompileWaste(limit: Int = 50): Command[Unit] = Task.Command {
    val report = analyzeStrictDepsWeight()()
    val snapshot = StrictDepsAnalyzer.compileWasteSnapshot(
      moduleName = moduleSegments.render,
      report = report
    )
    Task.log.info(
      "\n" + StrictDepsCompileWasteRenderer.render(
        snapshot = snapshot,
        limit = limit
      )
    )
    Result.Success(())
  }

  def strictDepsGraphSnapshot: T[StrictDepsGraphSnapshot] = Task {
    val currentNode = strictDepsGraphNode(
      moduleName = moduleSegments.render,
      module = outer,
      analysisFile = compile().analysisFile,
      sourceFiles = allSourceFiles()
    )
    val dependencyNodes = strictDepsDependencyGraphNodes()()

    StrictDepsGraphSnapshot(
      moduleName = currentNode.moduleName,
      modules = mergeGraphNodes(currentNode +: dependencyNodes)
    )
  }

  def strictDepsCompileWasteSnapshot: T[StrictDepsCompileWasteSnapshot] = Task {
    StrictDepsAnalyzer.compileWasteSnapshot(
      moduleName = moduleSegments.render,
      report = analyzeStrictDepsWeight()()
    )
  }

  def strictDepsCheck(): Command[Unit] = Task.Command {
    val report = analyzeStrictDeps()()
    val failures = Seq(
      Option.when(strictDepsFailOnUnusedDirectModuleDeps() && report.unusedDirectModuleDeps.nonEmpty)(
        "unused direct module deps: " + report.unusedDirectModuleDeps.mkString(", ")
      ),
      Option.when(
        strictDepsFailOnMissingDirectModuleDeps() && report.missingDirectModuleDeps.nonEmpty
      )(
        "missing direct module deps: " +
          report.missingDirectModuleDeps.map(_.moduleName).mkString(", ")
      )
    ).flatten

    if (failures.nonEmpty) {
      Result.Failure(
        "strictDepsCheck failed\n" + failures.map(message => s"- $message").mkString("\n")
      )
    } else {
      Task.log.info("strictDepsCheck passed")
      Result.Success(())
    }
  }

  private def analyzeStrictDeps(): Task[StrictDepsReport] = Task.Anon {
    val directModules = directCompileModules
    val transitiveSnapshots = strictDepsModuleSnapshots()()

    StrictDepsAnalyzer.analyze(
      currentAnalysisFile = compile().analysisFile,
      directModuleNames = directModules.map(_.toString).toSet,
      transitiveModules = transitiveSnapshots,
      ignoredModuleNames = strictDepsIgnoredModuleDeps().toSet
    )
  }

  private def analyzeStrictDepsWeight(): Task[StrictDepsWeightReport] =
    Task.Anon {
      StrictDepsAnalyzer.weightReport(
        currentAnalysisFile = compile().analysisFile,
        currentModuleSourceFiles = sourceFileIds(allSourceFiles()).toSet,
        directModuleNames = directCompileModules.map(_.toString).toSet,
        millTransitiveModules = strictDepsModuleWeightSnapshots()(),
        zincTransitiveModules = strictDepsModuleSnapshots()(),
        ignoredModuleNames = strictDepsIgnoredModuleDeps().toSet
      )
    }

  private def strictDepsModuleSnapshots(): Task[Seq[StrictDepsModuleSnapshot]] = Task.Anon {
    Task.traverse(outer.transitiveModuleCompileModuleDeps.distinct) { module =>
      Task.Anon {
        StrictDepsModuleSnapshot(
          moduleName = module.toString,
          analysisFile = module.compile().analysisFile,
          directDependencyModuleNames = directCompileModules(module)
            .map(_.toString)
            .distinct
            .sorted,
          sourceFiles = sourceFileIds(module.allSourceFiles())
        )
      }
    }()
  }

  private def strictDepsModuleWeightSnapshots(): Task[Seq[StrictDepsModuleWeightSnapshot]] = Task.Anon {
    Task.traverse(outer.transitiveModuleCompileModuleDeps.distinct) { module =>
      Task.Anon {
        StrictDepsModuleWeightSnapshot(
          moduleName = module.toString,
          sourceFiles = sourceFileIds(module.allSourceFiles()),
          directDependencyModuleNames = directCompileModules(module)
            .map(_.toString)
            .distinct
            .sorted
        )
      }
    }()
  }

  private def strictDepsDependencyGraphNodes(): Task[Seq[StrictDepsGraphModule]] = Task.Anon {
    Task.traverse(outer.transitiveModuleCompileModuleDeps.distinct) { module =>
      Task.Anon {
        strictDepsGraphNode(
          moduleName = module.toString,
          module = module,
          analysisFile = module.compile().analysisFile,
          sourceFiles = module.allSourceFiles()
        )
      }
    }()
  }

  private def directCompileModules: Seq[JavaModule] = {
    directCompileModules(outer)
  }

  private def directCompileModules(module: JavaModule): Seq[JavaModule] = {
    (module.moduleDepsChecked ++ module.compileModuleDepsChecked).distinct
  }

  private def sourceFileIds(files: Seq[PathRef]): Seq[String] = {
    files.map(_.path.toString).distinct.sorted
  }

  private def strictDepsGraphNode(
      moduleName: String,
      module: JavaModule,
      analysisFile: os.Path,
      sourceFiles: Seq[PathRef]
  ): StrictDepsGraphModule = {
    StrictDepsAnalyzer.graphModule(
      moduleName = moduleName,
      directDependencyModuleNames = directCompileModules(module)
        .map(_.toString)
        .distinct
        .sorted,
      analysisFile = analysisFile,
      sourceFiles = sourceFileIds(sourceFiles)
    )
  }

  private def mergeGraphNodes(nodes: Seq[StrictDepsGraphModule]): Seq[StrictDepsGraphModule] = {
    nodes
      .groupMapReduce(_.moduleName)(identity) { (left, right) =>
        StrictDepsGraphModule(
          moduleName = left.moduleName,
          directDependencyModuleNames = (left.directDependencyModuleNames ++ right.directDependencyModuleNames)
            .distinct
            .sorted,
          ownSourceCount = left.ownSourceCount.max(right.ownSourceCount),
          ownSourceLineCount = left.ownSourceLineCount.max(right.ownSourceLineCount),
          ownClassCount = left.ownClassCount.max(right.ownClassCount)
        )
      }
      .values
      .toSeq
      .sortBy(_.moduleName)
  }
}
