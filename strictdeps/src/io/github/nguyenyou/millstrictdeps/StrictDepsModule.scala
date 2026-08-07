package io.github.nguyenyou.millstrictdeps

import mill.*
import mill.api.BuildCtx
import mill.api.Result
import mill.javalib.JavaModule
import mill.javalib.StrictDepsZincCompiler
import mill.scalalib.ScalaModule

import java.nio.file.Paths

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

  /** Cache-tracked Zinc analysis used by all strict-deps tasks. */
  def strictDepsZincAnalysis: T[PathRef] = Task(persistent = true) {
    materializeZincAnalysis(
      module = outer,
      regenerationSubPath = os.sub / "regenerated",
      destinationSubPath = os.sub / "zinc"
    )()
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

  def strictDepsAutofixPlan: T[PathRef] = Task {
    val report = analyzeStrictDeps()()
    val input = strictDepsAutofixInput(report)
    val out = Task.dest / "strict-deps-autofix-plan.md"
    if (!os.exists(input.sourceFile)) {
      Result.Failure(s"Module source file does not exist: ${input.sourceFile}")
    } else {
      val plan = StrictDepsAutofix.plan(input, os.read(input.sourceFile))
      os.write.over(out, StrictDepsAutofixRenderer.render(plan, dryRun = true))
      Task.log.info(s"strictDepsAutofixPlan -> $out")
      Result.Success(PathRef(out))
    }
  }

  def strictDepsApplyFix(dryRun: Boolean = false): Command[Unit] = Task.Command(globalExclusive = true) {
    val report = analyzeStrictDeps()()
    val input = strictDepsAutofixInput(report)
    if (!os.exists(input.sourceFile)) {
      Result.Failure(s"Module source file does not exist: ${input.sourceFile}")
    } else {
      val source = os.read(input.sourceFile)
      val plan = StrictDepsAutofix.plan(input, source)
      Task.log.info("\n" + StrictDepsAutofixRenderer.render(plan, dryRun = dryRun))
      if (!plan.canApply) {
        Result.Failure(
          "strictDepsApplyFix refused to edit because at least one required change was unsafe"
        )
      } else if (dryRun || !plan.hasChanges) {
        Result.Success(())
      } else {
        os.write.over(input.sourceFile, plan.applyTo(source))
        Task.log.info(s"strictDepsApplyFix updated ${input.sourceFile}")
        Result.Success(())
      }
    }
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

  def strictDepsCompileDepth(
      zeroReachableSourcesOnly: Boolean = false,
      showSummary: Boolean = false
  ): Command[Unit] = Task.Command {
    val report = analyzeStrictDepsWeight()()
    Task.log.info(
      "\n" + StrictDepsCompileDepthRenderer.render(
        moduleName = moduleSegments.render,
        report = report,
        zeroReachableSourcesOnly = zeroReachableSourcesOnly,
        showSummary = showSummary
      )
    )
    Result.Success(())
  }

  def strictDepsWhoIntroduces(target: String): Command[Unit] = Task.Command {
    val report = StrictDepsAnalyzer.whoIntroduces(
      target = target,
      directModuleNames = directCompileModules.map(_.toString).toSet,
      transitiveModules = strictDepsModuleSnapshots()
    )
    Task.log.info(
      "\n" + StrictDepsWhoIntroducesRenderer.render(
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
      analysisFile = strictDepsZincAnalysis(),
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
    val transitiveSnapshots = strictDepsModuleSnapshots()

    StrictDepsAnalyzer.analyze(
      currentAnalysisFile = strictDepsZincAnalysis(),
      directModuleNames = directModules.map(_.toString).toSet,
      transitiveModules = transitiveSnapshots,
      ignoredModuleNames = strictDepsIgnoredModuleDeps().toSet
    )
  }

  private def analyzeStrictDepsWeight(): Task[StrictDepsWeightReport] =
    Task.Anon {
      StrictDepsAnalyzer.weightReport(
        currentAnalysisFile = strictDepsZincAnalysis(),
        currentModuleSourceFiles = sourceFileIds(allSourceFiles()).toSet,
        directModuleNames = directCompileModules.map(_.toString).toSet,
        millTransitiveModules = strictDepsModuleWeightSnapshots()(),
        zincTransitiveModules = strictDepsModuleSnapshots(),
        ignoredModuleNames = strictDepsIgnoredModuleDeps().toSet
      )
    }

  private def strictDepsModuleSnapshots: T[Seq[StrictDepsModuleSnapshot]] = Task(persistent = true) {
    Task.traverse(outer.transitiveModuleCompileModuleDeps.distinct.zipWithIndex) { case (module, index) =>
      Task.Anon {
        val analysisFile = dependencyZincAnalysis(module, index)()
        StrictDepsModuleSnapshot(
          moduleName = module.toString,
          analysisFile = analysisFile,
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
    strictDepsModuleSnapshots().map { snapshot =>
      StrictDepsAnalyzer.graphModule(
        moduleName = snapshot.moduleName,
        directDependencyModuleNames = snapshot.directDependencyModuleNames,
        analysisFile = snapshot.analysisFile,
        sourceFiles = snapshot.sourceFiles
      )
    }
  }

  private def directCompileModules: Seq[JavaModule] = {
    directCompileModules(outer)
  }

  private def directCompileModules(module: JavaModule): Seq[JavaModule] = {
    (module.moduleDepsChecked ++ module.compileModuleDepsChecked).distinct
  }

  private def strictDepsAutofixInput(report: StrictDepsReport): StrictDepsAutofix.Input = {
    val normalDirectDeps = outer.moduleDepsChecked.distinct
    val compileDirectDeps = outer.compileModuleDepsChecked.distinct
    val transitiveDeps = outer.transitiveModuleCompileModuleDeps.distinct
    StrictDepsAutofix.Input(
      moduleName = moduleSegments.render,
      moduleSegments = moduleSegments,
      sourceFile = moduleSourcePath,
      moduleLine = moduleCtx.lineNum,
      normalDirectDeps = normalDirectDeps.map(strictDepsAutofixModuleRef),
      compileDirectDeps = compileDirectDeps.map(strictDepsAutofixModuleRef),
      transitiveDeps = transitiveDeps.map(strictDepsAutofixModuleRef),
      missingDirectDeps = report.missingDirectModuleDeps.map(_.moduleName),
      unusedDirectDeps = report.unusedDirectModuleDeps
    )
  }

  private def strictDepsAutofixModuleRef(module: JavaModule): StrictDepsAutofix.ModuleRef = {
    StrictDepsAutofix.ModuleRef(
      moduleName = module.toString,
      segments = module.moduleSegments,
      directDependencyModuleNames = directCompileModules(module).map(_.toString).distinct.sorted
    )
  }

  private def moduleSourcePath: os.Path = {
    val path = Paths.get(moduleCtx.fileName)
    if (path.isAbsolute) {
      os.Path(path)
    } else {
      val taskRelativePath = PathRef.toResolvedOsPath(os.Path(path, os.pwd))
      if (os.exists(taskRelativePath)) {
        taskRelativePath
      } else {
        BuildCtx.workspaceRoot / os.RelPath(moduleCtx.fileName)
      }
    }
  }

  private def sourceFileIds(files: Seq[PathRef]): Seq[String] = {
    files.map(_.path.toString).distinct.sorted
  }

  private def strictDepsGraphNode(
      moduleName: String,
      module: JavaModule,
      analysisFile: PathRef,
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

  private def materializeZincAnalysis(
      module: JavaModule,
      regenerationSubPath: os.SubPath,
      destinationSubPath: os.SubPath
  ): Task[PathRef] = Task.Anon {
    val compilation = module.compile()
    val preparedCompiler = StrictDepsZincCompiler.prepare(module)()
    val analysisFile = if (StrictDepsZincFile.containsAnalysis(compilation.analysisFile)) {
      compilation.analysisFile
    } else {
      Task.log.info(s"Regenerating missing Zinc analysis for ${module.toString}")
      val regenerated = preparedCompiler.regenerate(Task.dest / regenerationSubPath)
      if (!StrictDepsZincFile.containsAnalysis(regenerated.analysisFile)) {
        Task.fail(s"Unable to regenerate Zinc analysis for ${module.toString}")
      }
      regenerated.analysisFile
    }
    StrictDepsZincFile.materialize(analysisFile, Task.dest / destinationSubPath)
  }

  private def dependencyZincAnalysis(module: JavaModule, index: Int): Task[PathRef] = {
    module match {
      case strictDepsModule: StrictDepsModule => strictDepsModule.strictDepsZincAnalysis
      case _ =>
        materializeZincAnalysis(
          module = module,
          regenerationSubPath = os.sub / "regenerated" / index.toString,
          destinationSubPath = os.sub / "analyses" / index.toString / "zinc"
        )
    }
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
