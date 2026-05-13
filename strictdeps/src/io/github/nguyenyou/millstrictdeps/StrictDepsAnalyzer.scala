package io.github.nguyenyou.millstrictdeps

import scala.collection.mutable
import scala.math.round

import sbt.internal.inc.Analysis
import sbt.internal.inc.FileAnalysisStore

object StrictDepsAnalyzer {

  def weightReport(
      currentAnalysisFile: os.Path,
      currentModuleSourceFiles: Set[String],
      directModuleNames: Set[String],
      millTransitiveModules: Seq[StrictDepsModuleWeightSnapshot],
      zincTransitiveModules: Seq[StrictDepsModuleSnapshot],
      ignoredModuleNames: Set[String]
  ): StrictDepsWeightReport = {
    val currentMillSources = currentModuleSourceFiles
    val currentZincSources = sourceFiles(readAnalysis(currentAnalysisFile))
    val millDependencyModules = millTransitiveModules.map(weightModule)
    val zincDependencyModules = zincTransitiveModules.map(analyzeDependencyModule)
    val millDependencySources = millDependencyModules.flatMap(_.sources).toSet
    val zincDependencySources = zincDependencyModules.flatMap(_.sources).toSet
    val millDependencyWeightSources = computeDependencyWeightSources(
      dependencyModules = millDependencyModules,
      ignoredModuleNames = ignoredModuleNames
    ).map(weight => weight.moduleName -> weight).toMap
    val zincDependencyWeightSources = computeDependencyWeightSources(
      dependencyModules = zincDependencyModules,
      ignoredModuleNames = ignoredModuleNames
    ).map(weight => weight.moduleName -> weight).toMap
    val dependencyWeightsWithoutDelta = (millDependencyWeightSources.keySet ++ zincDependencyWeightSources.keySet).toSeq
      .sorted
      .map { moduleName =>
        val millWeight = millDependencyWeightSources.get(moduleName)
        val zincWeight = zincDependencyWeightSources.get(moduleName)
        StrictDepsModuleWeightComparison(
          moduleName = moduleName,
          declaredDirect = directModuleNames.contains(moduleName),
          ownSources = StrictDepsSourceWeightComparison(
            millSourceCount = millWeight.map(_.ownSources.size).getOrElse(0),
            zincSourceCount = zincWeight.map(_.ownSources.size).getOrElse(0)
          ),
          absoluteSources = StrictDepsSourceWeightComparison(
            millSourceCount = millWeight.map(_.absoluteSources.size).getOrElse(0),
            zincSourceCount = zincWeight.map(_.absoluteSources.size).getOrElse(0)
          )
        )
      }
    val sortedDependencyWeightModuleNames = dependencyWeightsWithoutDelta
      .sortBy(weightSortKey)
      .map(_.moduleName)
    val millDeltaSourceCounts = computeDeltaSourceCounts(
      sortedModuleNames = sortedDependencyWeightModuleNames,
      dependencyWeightSourcesByModule = millDependencyWeightSources
    )
    val zincDeltaSourceCounts = computeDeltaSourceCounts(
      sortedModuleNames = sortedDependencyWeightModuleNames,
      dependencyWeightSourcesByModule = zincDependencyWeightSources
    )
    val dependencyWeights = dependencyWeightsWithoutDelta.map { weight =>
      weight.copy(
        deltaSources = StrictDepsSourceWeightComparison(
          millSourceCount = millDeltaSourceCounts.getOrElse(weight.moduleName, 0),
          zincSourceCount = zincDeltaSourceCounts.getOrElse(weight.moduleName, 0)
        )
      )
    }
    val compileDepthData = compileDepthReport(
      directModuleNames = directModuleNames,
      dependencyWeights = dependencyWeights,
      dependencyModules = millDependencyModules ++ zincDependencyModules
    )

    StrictDepsWeightReport(
      currentModuleSources = StrictDepsSourceWeightComparison(
        millSourceCount = currentMillSources.size,
        zincSourceCount = currentZincSources.size
      ),
      dependencySources = StrictDepsSourceWeightComparison(
        millSourceCount = millDependencySources.size,
        zincSourceCount = zincDependencySources.size
      ),
      totalSources = StrictDepsSourceWeightComparison(
        millSourceCount = currentMillSources.union(millDependencySources).size,
        zincSourceCount = currentZincSources.union(zincDependencySources).size
      ),
      dependencyWeights = dependencyWeights,
      compileDepths = compileDepthData.depths,
      targetDepthIndex = compileDepthData.targetDepthIndex
    )
  }

  def dependencyWeights(
      directModuleNames: Set[String],
      transitiveModules: Seq[StrictDepsModuleSnapshot],
      ignoredModuleNames: Set[String]
  ): Seq[StrictDepsModuleDependencyWeight] = {
    computeDependencyWeights(
      directModuleNames = directModuleNames,
      dependencyModules = transitiveModules.map(analyzeDependencyModule),
      ignoredModuleNames = ignoredModuleNames
    )
  }

  def analyze(
      currentAnalysisFile: os.Path,
      directModuleNames: Set[String],
      transitiveModules: Seq[StrictDepsModuleSnapshot],
      ignoredModuleNames: Set[String]
  ): StrictDepsReport = {
    val currentAnalysis = readAnalysis(currentAnalysisFile)
    val usedExternalClasses = currentAnalysis.relations.allExternalDeps.toSeq
      .map(normalizeUsedClassName)
      .sorted
      .distinct
    val analyzedModules = transitiveModules.map(analyzeModule)
    val moduleClasses = analyzedModules
      .map(module => module.moduleName -> module.definedClasses)
      .filter { case (_, classes) => classes.nonEmpty }
    val moduleClassesByName = moduleClasses.toMap

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
    val relevantUsedClassesByModule = usedClassesByModule.filterNot { case (moduleName, _) =>
      ignored.contains(moduleName)
    }
    val totalInternalUsedClassRefs = relevantUsedClassesByModule.values.map(_.size).sum

    val dependencyUsageWeights = relevantUsedClassesByModule.toSeq
      .sortBy { case (moduleName, classes) => (-classes.size, moduleName) }
      .map { case (moduleName, classes) =>
        val dependencyClassCount = moduleClassesByName.getOrElse(moduleName, Set.empty).size
        StrictDepsModuleUsageWeight(
          moduleName = moduleName,
          declaredDirect = directModuleNames.contains(moduleName),
          usedClasses = classes,
          dependencyClassCount = dependencyClassCount,
          currentModuleUsagePercent = percent(classes.size, totalInternalUsedClassRefs),
          dependencyTouchedPercent = percent(classes.size, dependencyClassCount)
        )
      }

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

    val dependencyWeights = computeDependencyWeights(
      directModuleNames = directModuleNames,
      dependencyModules = analyzedModules.map(dependencyModule),
      ignoredModuleNames = ignoredModuleNames
    )

    val reachability = analyzeReachability(
      usedExternalClasses = usedExternalClasses.toSet,
      directModuleNames = directModuleNames,
      analyzedModules = analyzedModules,
      ignoredModuleNames = ignoredModuleNames
    )

    StrictDepsReport(
      usedDirectModuleDeps = usedDirectModuleDeps,
      unusedDirectModuleDeps = unusedDirectModuleDeps,
      missingDirectModuleDeps = missingDirectModuleDeps,
      usedLibraryClasspathEntries = usedLibraryClasspathEntries,
      dependencyUsageWeights = dependencyUsageWeights,
      dependencyWeights = dependencyWeights,
      reachability = reachability
    )
  }

  private def analyzeDependencyModule(module: StrictDepsModuleSnapshot): DependencyModule = {
    val analysis = readAnalysis(module.analysisFile)
    DependencyModule(
      moduleName = module.moduleName,
      directDependencyModuleNames = module.directDependencyModuleNames.distinct.sorted,
      sources = sourceFiles(analysis)
    )
  }

  private def weightModule(module: StrictDepsModuleWeightSnapshot): DependencyModule = {
    DependencyModule(
      moduleName = module.moduleName,
      directDependencyModuleNames = module.directDependencyModuleNames.distinct.sorted,
      sources = module.sourceFiles.toSet
    )
  }

  private def analyzeModule(module: StrictDepsModuleSnapshot): AnalyzedModule = {
    val analysis = readAnalysis(module.analysisFile)
    val rawClasses = definedClasses(analysis)
    val defined = rawClasses.map(normalizeUsedClassName)
    val allSources = sourceFiles(analysis)
    val sourcesByClass = analysis.relations.allSources.toSeq
      .flatMap { source =>
        analysis.relations
          .classNames(source)
          .map(normalizeUsedClassName)
          .map(className => className -> source.id)
      }
      .groupMap { case (className, _) => className } { case (_, source) => source }
      .view
      .mapValues(_.distinct.sorted.toSet)
      .toMap

    AnalyzedModule(
      moduleName = module.moduleName,
      analysis = analysis,
      rawClasses = rawClasses,
      definedClasses = defined,
      directDependencyModuleNames = module.directDependencyModuleNames.distinct.sorted,
      sourcesByClass = sourcesByClass,
      sources = allSources
    )
  }

  private def computeDependencyWeights(
      directModuleNames: Set[String],
      dependencyModules: Seq[DependencyModule],
      ignoredModuleNames: Set[String]
  ): Seq[StrictDepsModuleDependencyWeight] = {
    val dependencyGraph = dependencyModules
      .map(module => module.moduleName -> module.directDependencyModuleNames.toSet)
      .toMap
    val sourcesByModule = dependencyModules
      .map(module => module.moduleName -> module.sources)
      .toMap
    val currentDependencySources = sourcesForDependencyRoots(
      rootModuleNames = directModuleNames,
      dependencyGraph = dependencyGraph,
      sourcesByModule = sourcesByModule
    )

    dependencyModules
      .filterNot(module => ignoredModuleNames.contains(module.moduleName))
      .map { module =>
        val moduleClosure = dependencyModuleClosure(module.moduleName, dependencyGraph)
        val moduleSources = sourcesForModules(moduleClosure, sourcesByModule)
        val transitiveDependencyModuleNames = moduleClosure
          .filterNot(_ == module.moduleName)
          .toSeq
          .sorted
        val declaredDirect = directModuleNames.contains(module.moduleName)
        val deltaSourceCount =
          if (declaredDirect) {
            val dependencySourcesWithoutModule = sourcesForDependencyRoots(
              rootModuleNames = directModuleNames.filterNot(_ == module.moduleName),
              dependencyGraph = dependencyGraph,
              sourcesByModule = sourcesByModule
            )
            currentDependencySources.diff(dependencySourcesWithoutModule).size
          } else {
            moduleSources.diff(currentDependencySources).size
          }
        val deltaKind =
          if (declaredDirect) {
            "remove"
          } else {
            "add"
          }

        StrictDepsModuleDependencyWeight(
          moduleName = module.moduleName,
          declaredDirect = declaredDirect,
          directDependencyModuleNames = module.directDependencyModuleNames,
          transitiveDependencyModuleNames = transitiveDependencyModuleNames,
          ownSourceCount = module.sources.size,
          absoluteSourceCount = moduleSources.size,
          deltaSourceCount = deltaSourceCount,
          deltaKind = deltaKind
        )
      }
      .sortBy { weight =>
        (
          -weight.deltaSourceCount,
          -weight.absoluteSourceCount,
          if (weight.declaredDirect) 0 else 1,
          weight.moduleName
        )
      }
  }

  private def computeDependencyWeightSources(
      dependencyModules: Seq[DependencyModule],
      ignoredModuleNames: Set[String]
  ): Seq[DependencyWeightSources] = {
    val dependencyGraph = dependencyModules
      .map(module => module.moduleName -> module.directDependencyModuleNames.toSet)
      .toMap
    val sourcesByModule = dependencyModules
      .map(module => module.moduleName -> module.sources)
      .toMap

    dependencyModules
      .filterNot(module => ignoredModuleNames.contains(module.moduleName))
      .map { module =>
        val moduleClosure = dependencyModuleClosure(module.moduleName, dependencyGraph)

        DependencyWeightSources(
          moduleName = module.moduleName,
          ownSources = module.sources,
          absoluteSources = sourcesForModules(moduleClosure, sourcesByModule)
        )
      }
  }

  private def computeDeltaSourceCounts(
      sortedModuleNames: Seq[String],
      dependencyWeightSourcesByModule: Map[String, DependencyWeightSources]
  ): Map[String, Int] = {
    val seenSources = mutable.Set.empty[String]

    sortedModuleNames.map { moduleName =>
      val absoluteSources = dependencyWeightSourcesByModule
        .get(moduleName)
        .map(_.absoluteSources)
        .getOrElse(Set.empty)
      val deltaSourceCount = absoluteSources.count(source => !seenSources.contains(source))
      seenSources ++= absoluteSources

      moduleName -> deltaSourceCount
    }.toMap
  }

  private def weightSortKey(weight: StrictDepsModuleWeightComparison): (Int, Int, String) = {
    (
      -weight.absoluteSources.maxSourceCount,
      if (weight.declaredDirect) 0 else 1,
      weight.moduleName
    )
  }

  private def compileDepthReport(
      directModuleNames: Set[String],
      dependencyWeights: Seq[StrictDepsModuleWeightComparison],
      dependencyModules: Seq[DependencyModule]
  ): CompileDepthData = {
    val visibleModuleNames = dependencyWeights.map(_.moduleName).toSet
    val dependencyGraph = dependencyModules
      .groupMapReduce(_.moduleName)(_.directDependencyModuleNames.toSet)(_ ++ _)
      .view
      .mapValues(_.intersect(visibleModuleNames))
      .toMap
    val weightsByModule = dependencyWeights.map(weight => weight.moduleName -> weight).toMap
    val depthLevels = compileDepthLevels(
      moduleNames = visibleModuleNames,
      dependencyGraph = dependencyGraph
    )
    val depths = depthLevels.toSeq
      .groupMap { case (_, depthIndex) => depthIndex } { case (moduleName, _) =>
        weightsByModule(moduleName)
      }
      .toSeq
      .sortBy { case (depthIndex, _) => depthIndex }
      .map { case (depthIndex, modules) =>
        StrictDepsCompileDepth(
          index = depthIndex,
          modules = modules.sortBy { weight =>
            (
              -weight.absoluteSources.maxSourceCount,
              if (weight.declaredDirect) 0 else 1,
              weight.moduleName
            )
          }
        )
      }
    val targetDepthIndex = directModuleNames
      .flatMap(depthLevels.get)
      .maxOption
      .map(_ + 1)
      .getOrElse(0)

    CompileDepthData(
      depths = depths,
      targetDepthIndex = targetDepthIndex
    )
  }

  private def compileDepthLevels(
      moduleNames: Set[String],
      dependencyGraph: Map[String, Set[String]]
  ): Map[String, Int] = {
    val memo = mutable.Map.empty[String, Int]
    val visiting = mutable.Set.empty[String]

    def depthLevel(moduleName: String): Int = {
      memo.getOrElseUpdate(
        moduleName, {
          if (visiting.contains(moduleName)) {
            0
          } else {
            visiting += moduleName
            val dependencyLevels = dependencyGraph
              .getOrElse(moduleName, Set.empty)
              .filter(moduleNames.contains)
              .map(depthLevel)
            visiting -= moduleName

            if (dependencyLevels.isEmpty) {
              0
            } else {
              dependencyLevels.max + 1
            }
          }
        }
      )
    }

    moduleNames.toSeq.sorted.foreach(depthLevel)
    memo.toMap
  }

  private def analyzeReachability(
      usedExternalClasses: Set[String],
      directModuleNames: Set[String],
      analyzedModules: Seq[AnalyzedModule],
      ignoredModuleNames: Set[String]
  ): StrictDepsReachabilityReport = {
    val allProvidedClasses = analyzedModules.flatMap(_.definedClasses).toSet
    val relevantModules = analyzedModules.filterNot(module => ignoredModuleNames.contains(module.moduleName))
    val directlyUsedProvidedClasses = usedExternalClasses.intersect(allProvidedClasses)
    val graph = classDependencyGraph(analyzedModules, allProvidedClasses)
    val reachableProvidedClasses = reachableClasses(directlyUsedProvidedClasses, graph)
      .intersect(allProvidedClasses)

    val moduleReachability = relevantModules
      .map { module =>
        val providedClasses = module.definedClasses.toSeq.sorted
        val directUsedClasses = providedClasses.filter(directlyUsedProvidedClasses.contains)
        val reachableClasses = providedClasses.filter(reachableProvidedClasses.contains)
        val unusedClasses = providedClasses.filterNot(reachableProvidedClasses.contains)
        val directUsedSources = sourcesForClasses(module, directUsedClasses.toSet)
        val reachableSources = sourcesForClasses(module, reachableClasses.toSet)
        val unusedSources = module.sources.diff(reachableSources.toSet).toSeq.sorted

        StrictDepsModuleReachability(
          moduleName = module.moduleName,
          declaredDirect = directModuleNames.contains(module.moduleName),
          providedClasses = providedClasses,
          directUsedClasses = directUsedClasses,
          reachableClasses = reachableClasses,
          unusedClasses = unusedClasses,
          providedSources = module.sources.toSeq.sorted,
          directUsedSources = directUsedSources,
          reachableSources = reachableSources,
          unusedSources = unusedSources,
          reachableClassPercent = percent(reachableClasses.size, providedClasses.size),
          reachableSourcePercent = percent(reachableSources.size, module.sources.size)
        )
      }
      .sortBy { module =>
        (-module.unusedSourceCount, -module.unusedClassCount, module.moduleName)
      }

    val providedClassCount = moduleReachability.map(_.providedClassCount).sum
    val directUsedClassCount = moduleReachability.map(_.directUsedClassCount).sum
    val reachableClassCount = moduleReachability.map(_.reachableClassCount).sum
    val providedSourceCount = moduleReachability.map(_.providedSourceCount).sum
    val directUsedSourceCount = moduleReachability.map(_.directUsedSourceCount).sum
    val reachableSourceCount = moduleReachability.map(_.reachableSourceCount).sum

    StrictDepsReachabilityReport(
      providedClassCount = providedClassCount,
      directUsedClassCount = directUsedClassCount,
      reachableClassCount = reachableClassCount,
      unusedClassCount = providedClassCount - reachableClassCount,
      reachableClassPercent = percent(reachableClassCount, providedClassCount),
      providedSourceCount = providedSourceCount,
      directUsedSourceCount = directUsedSourceCount,
      reachableSourceCount = reachableSourceCount,
      unusedSourceCount = providedSourceCount - reachableSourceCount,
      reachableSourcePercent = percent(reachableSourceCount, providedSourceCount),
      modules = moduleReachability
    )
  }

  private def classDependencyGraph(
      analyzedModules: Seq[AnalyzedModule],
      allProvidedClasses: Set[String]
  ): Map[String, Set[String]] = {
    analyzedModules
      .flatMap { module =>
        module.rawClasses.map { rawClassName =>
          val sourceClassName = normalizeUsedClassName(rawClassName)
          val dependencyClasses =
            module.analysis.relations.internalClassDeps(rawClassName).map(normalizeUsedClassName) ++
              module.analysis.relations.externalDeps(rawClassName).map(normalizeUsedClassName)
          sourceClassName -> dependencyClasses.filter(allProvidedClasses.contains)
        }
      }
      .groupMap { case (sourceClassName, _) => sourceClassName } { case (_, dependencyClasses) =>
        dependencyClasses
      }
      .view
      .mapValues(_.foldLeft(Set.empty[String])(_ ++ _))
      .toMap
  }

  private def reachableClasses(
      rootClasses: Set[String],
      dependencyGraph: Map[String, Set[String]]
  ): Set[String] = {
    val seen = mutable.Set.empty[String]
    val queue = mutable.Queue.empty[String]
    rootClasses.toSeq.sorted.foreach(queue.enqueue(_))

    while (queue.nonEmpty) {
      val className = queue.dequeue()
      if (!seen.contains(className)) {
        seen += className
        dependencyGraph.getOrElse(className, Set.empty).toSeq.sorted.foreach { dependencyClass =>
          if (!seen.contains(dependencyClass)) {
            queue.enqueue(dependencyClass)
          }
        }
      }
    }

    seen.toSet
  }

  private def dependencyModuleClosure(
      moduleName: String,
      dependencyGraph: Map[String, Set[String]]
  ): Set[String] = {
    val seen = mutable.Set.empty[String]
    val queue = mutable.Queue.empty[String]
    queue.enqueue(moduleName)

    while (queue.nonEmpty) {
      val currentModuleName = queue.dequeue()
      if (!seen.contains(currentModuleName)) {
        seen += currentModuleName
        val dependencyModuleNames = dependencyGraph
          .getOrElse(currentModuleName, Set.empty)
          .toSeq
          .sorted
        dependencyModuleNames.foreach { dependencyModuleName =>
          if (!seen.contains(dependencyModuleName)) {
            queue.enqueue(dependencyModuleName)
          }
        }
      }
    }

    seen.toSet
  }

  private def sourcesForDependencyRoots(
      rootModuleNames: Set[String],
      dependencyGraph: Map[String, Set[String]],
      sourcesByModule: Map[String, Set[String]]
  ): Set[String] = {
    sourcesForModules(
      rootModuleNames.flatMap { moduleName =>
        dependencyModuleClosure(moduleName, dependencyGraph)
      },
      sourcesByModule
    )
  }

  private def sourcesForModules(
      moduleNames: Set[String],
      sourcesByModule: Map[String, Set[String]]
  ): Set[String] = {
    moduleNames.flatMap(moduleName => sourcesByModule.getOrElse(moduleName, Set.empty))
  }

  private def sourcesForClasses(module: AnalyzedModule, classNames: Set[String]): Seq[String] = {
    classNames.toSeq
      .flatMap(className => module.sourcesByClass.getOrElse(className, Set.empty))
      .distinct
      .sorted
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

  private def sourceFiles(analysis: Analysis): Set[String] = {
    analysis.relations.allSources.toSeq.map(_.id).distinct.sorted.toSet
  }

  private def normalizeUsedClassName(className: String): String = {
    className.stripSuffix("$")
  }

  private def percent(numerator: Int, denominator: Int): Double = {
    if (denominator == 0) {
      0.0
    } else {
      round(numerator.toDouble * 1000.0 / denominator).toDouble / 10.0
    }
  }

  private final case class AnalyzedModule(
      moduleName: String,
      analysis: Analysis,
      rawClasses: Set[String],
      definedClasses: Set[String],
      directDependencyModuleNames: Seq[String],
      sourcesByClass: Map[String, Set[String]],
      sources: Set[String]
  )

  private final case class DependencyModule(
      moduleName: String,
      directDependencyModuleNames: Seq[String],
      sources: Set[String]
  )

  private final case class DependencyWeightSources(
      moduleName: String,
      ownSources: Set[String],
      absoluteSources: Set[String]
  )

  private final case class CompileDepthData(
      depths: Seq[StrictDepsCompileDepth],
      targetDepthIndex: Int
  )

  private def dependencyModule(module: AnalyzedModule): DependencyModule = {
    DependencyModule(
      moduleName = module.moduleName,
      directDependencyModuleNames = module.directDependencyModuleNames,
      sources = module.sources
    )
  }
}
