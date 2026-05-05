package io.github.nguyenyou.millstrictdeps

import sbt.internal.inc.Analysis
import sbt.internal.inc.FileAnalysisStore

object StrictDepsAnalyzer {

  def analyze(
      currentAnalysisFile: os.Path,
      directModuleNames: Set[String],
      transitiveModules: Seq[StrictDepsModuleSnapshot],
      ignoredModuleNames: Set[String]
  ): StrictDepsReport = {
    val currentAnalysis = readAnalysis(currentAnalysisFile)
    val usedExternalClasses = currentAnalysis.relations.allExternalDeps.toSeq.sorted.distinct
    val moduleClasses = transitiveModules.map { module =>
      module.moduleName -> definedClasses(readAnalysis(module.analysisFile))
    }.filter { case (_, classes) => classes.nonEmpty }

    val ownersByClass = moduleClasses
      .flatMap { case (moduleName, classes) =>
        classes.map(className => className -> moduleName)
      }
      .groupMap { case (className, _) => className } { case (_, moduleName) => moduleName }
      .view
      .mapValues(_.distinct.sorted)
      .toMap

    val usedModuleClassPairs = usedExternalClasses.flatMap { className =>
      ownersByClass.getOrElse(className, Seq.empty).map { moduleName =>
        moduleName -> className
      }
    }

    val usedClassesByModule = usedModuleClassPairs
      .groupMap { case (moduleName, _) => moduleName } { case (_, className) => className }
      .view
      .mapValues(_.distinct.sorted)
      .toMap

    val ignored = ignoredModuleNames
    val usedDirectModuleDeps = directModuleNames.toSeq.sorted
      .filter(moduleName => usedClassesByModule.contains(moduleName))
      .filterNot(ignored.contains)
      .map(moduleName => StrictDepsModuleUsage(moduleName, usedClassesByModule(moduleName)))

    val unusedDirectModuleDeps = directModuleNames.toSeq.sorted
      .filterNot(moduleName => usedClassesByModule.contains(moduleName))
      .filterNot(ignored.contains)

    val missingDirectModuleDeps = usedClassesByModule.toSeq
      .filter { case (moduleName, _) => !directModuleNames.contains(moduleName) }
      .filterNot { case (moduleName, _) => ignored.contains(moduleName) }
      .sortBy { case (moduleName, classes) => (-classes.size, moduleName) }
      .map { case (moduleName, classes) => StrictDepsModuleUsage(moduleName, classes) }

    val usedLibraryClasspathEntries = currentAnalysis.relations.allLibraryDeps.toSeq
      .map(_.id)
      .distinct
      .sorted

    StrictDepsReport(
      usedDirectModuleDeps = usedDirectModuleDeps,
      unusedDirectModuleDeps = unusedDirectModuleDeps,
      missingDirectModuleDeps = missingDirectModuleDeps,
      usedLibraryClasspathEntries = usedLibraryClasspathEntries
    )
  }

  private def readAnalysis(analysisFile: os.Path): Analysis = {
    val contents = FileAnalysisStore
      .binary(analysisFile.toIO)
      .get()
    if (contents.isEmpty) {
      sys.error(s"No Zinc analysis found at $analysisFile")
    }
    contents
      .get()
      .getAnalysis
      .asInstanceOf[Analysis]
  }

  private def definedClasses(analysis: Analysis): Set[String] = {
    analysis.relations.classes._2s.toSet
  }
}
