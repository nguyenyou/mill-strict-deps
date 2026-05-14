package io.github.nguyenyou.millstrictdeps

import upickle.default.ReadWriter
import upickle.default.macroRW

final case class StrictDepsReport(
    usedDirectModuleDeps: Seq[StrictDepsModuleUsage],
    unusedDirectModuleDeps: Seq[String],
    missingDirectModuleDeps: Seq[StrictDepsModuleUsage],
    usedLibraryClasspathEntries: Seq[String],
    dependencyUsageWeights: Seq[StrictDepsModuleUsageWeight] = Seq.empty,
    dependencyWeights: Seq[StrictDepsModuleDependencyWeight] = Seq.empty,
    reachability: StrictDepsReachabilityReport = StrictDepsReachabilityReport.empty
) {
  def hasProblems: Boolean = {
    unusedDirectModuleDeps.nonEmpty || missingDirectModuleDeps.nonEmpty
  }
}

final case class StrictDepsModuleUsage(
    moduleName: String,
    usedClasses: Seq[String]
) {
  def usedClassCount: Int = usedClasses.size
}

final case class StrictDepsModuleSnapshot(
    moduleName: String,
    analysisFile: os.Path,
    directDependencyModuleNames: Seq[String] = Seq.empty,
    sourceFiles: Seq[String] = Seq.empty
)

final case class StrictDepsModuleWeightSnapshot(
    moduleName: String,
    sourceFiles: Seq[String],
    directDependencyModuleNames: Seq[String] = Seq.empty
)

final case class StrictDepsModuleDependencyWeight(
    moduleName: String,
    declaredDirect: Boolean,
    directDependencyModuleNames: Seq[String],
    transitiveDependencyModuleNames: Seq[String],
    ownSourceCount: Int,
    absoluteSourceCount: Int,
    deltaSourceCount: Int,
    deltaKind: String,
    ownSourceLineCount: Int = 0,
    absoluteSourceLineCount: Int = 0,
    deltaSourceLineCount: Int = 0,
    ownClassCount: Int = 0,
    absoluteClassCount: Int = 0
) {
  def directDependencyModuleCount: Int = directDependencyModuleNames.size
  def transitiveDependencyModuleCount: Int = transitiveDependencyModuleNames.size
  def absoluteModuleCount: Int = transitiveDependencyModuleCount + 1
}

final case class StrictDepsWeightReport(
    currentModuleSources: StrictDepsSourceWeightComparison,
    dependencySources: StrictDepsSourceWeightComparison,
    totalSources: StrictDepsSourceWeightComparison,
    currentModuleSourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    dependencySourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    totalSourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    currentModuleClassCount: Int = 0,
    dependencyClassCount: Int = 0,
    totalClassCount: Int = 0,
    dependencyWeights: Seq[StrictDepsModuleWeightComparison],
    compileDepths: Seq[StrictDepsCompileDepth] = Seq.empty,
    targetDepthIndex: Int = 0,
    reachability: StrictDepsReachabilityReport = StrictDepsReachabilityReport.empty
)

final case class StrictDepsSourceWeightComparison(
    millSourceCount: Int,
    zincSourceCount: Int
) {
  def matches: Boolean = millSourceCount == zincSourceCount
  def maxSourceCount: Int = millSourceCount.max(zincSourceCount)
}

final case class StrictDepsModuleWeightComparison(
    moduleName: String,
    declaredDirect: Boolean,
    ownSources: StrictDepsSourceWeightComparison,
    absoluteSources: StrictDepsSourceWeightComparison,
    deltaSources: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    compileDepthDeltaSources: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    ownSourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    absoluteSourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    deltaSourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    compileDepthDeltaSourceLines: StrictDepsSourceWeightComparison = StrictDepsSourceWeightComparison(0, 0),
    ownClassCount: Int = 0,
    absoluteClassCount: Int = 0,
    usedClassCount: Int = 0,
    usedClassTotalCount: Int = 0,
    usedClassPercent: Double = 0.0,
    reachableClassCount: Int = 0,
    reachableClassTotalCount: Int = 0,
    reachableClassPercent: Double = 0.0,
    reachableSourceCount: Int = 0,
    reachableSourceTotalCount: Int = 0,
    reachableSourcePercent: Double = 0.0,
    reachableDeltaSourceCount: Int = 0,
    wastedDeltaSourceCount: Int = 0,
    wastedDeltaSourcePercent: Double = 0.0,
    wastedOwnSourceCount: Int = 0,
    wastedClassCount: Int = 0,
    introducedByModuleNames: Seq[String] = Seq.empty
)

final case class StrictDepsCompileDepth(
    index: Int,
    modules: Seq[StrictDepsModuleWeightComparison]
)

final case class StrictDepsGraphSnapshot(
    moduleName: String,
    modules: Seq[StrictDepsGraphModule]
)

object StrictDepsGraphSnapshot {
  given ReadWriter[StrictDepsGraphSnapshot] = macroRW
}

final case class StrictDepsGraphModule(
    moduleName: String,
    directDependencyModuleNames: Seq[String],
    ownSourceCount: Int,
    ownSourceLineCount: Int = 0,
    ownClassCount: Int = 0
)

object StrictDepsGraphModule {
  given ReadWriter[StrictDepsGraphModule] = macroRW
}

final case class StrictDepsCompileWasteSnapshot(
    moduleName: String,
    dependencySourceCount: Int,
    reachableDependencySourceCount: Int,
    wastedDependencySourceCount: Int,
    dependencyClassCount: Int,
    reachableDependencyClassCount: Int,
    wastedDependencyClassCount: Int,
    deltaSourceCount: Int,
    reachableDeltaSourceCount: Int,
    wastedDeltaSourceCount: Int,
    dependencies: Seq[StrictDepsCompileWasteDependency]
)

object StrictDepsCompileWasteSnapshot {
  given ReadWriter[StrictDepsCompileWasteSnapshot] = macroRW
}

final case class StrictDepsCompileWasteDependency(
    moduleName: String,
    declaredDirect: Boolean,
    introducedByModuleNames: Seq[String],
    ownSourceCount: Int,
    reachableSourceCount: Int,
    wastedOwnSourceCount: Int,
    reachableSourcePercent: Double,
    deltaSourceCount: Int,
    reachableDeltaSourceCount: Int,
    wastedDeltaSourceCount: Int,
    wastedDeltaSourcePercent: Double,
    ownClassCount: Int,
    reachableClassCount: Int,
    wastedClassCount: Int,
    reachableClassPercent: Double
) {
  def relationship: String = {
    if (declaredDirect) {
      "direct"
    } else {
      "transitive"
    }
  }
}

object StrictDepsCompileWasteDependency {
  given ReadWriter[StrictDepsCompileWasteDependency] = macroRW
}

final case class StrictDepsCompileWasteGlobalReport(
    rootModuleCount: Int,
    dependencyModuleCount: Int,
    dependencyEdgeCount: Int,
    totalDeltaSourceCount: Int,
    totalReachableDeltaSourceCount: Int,
    totalWastedDeltaSourceCount: Int,
    wastedDeltaSourcePercent: Double,
    badNodes: Seq[StrictDepsCompileWasteNode],
    badEdges: Seq[StrictDepsCompileWasteEdge]
)

final case class StrictDepsCompileWasteNode(
    moduleName: String,
    neededByModuleCount: Int,
    directNeededByModuleCount: Int,
    totalDeltaSourceCount: Int,
    totalReachableDeltaSourceCount: Int,
    totalWastedDeltaSourceCount: Int,
    wastedDeltaSourcePercent: Double,
    totalOwnSourceCount: Int,
    totalReachableSourceCount: Int,
    totalWastedOwnSourceCount: Int,
    reachableSourcePercent: Double,
    maxOwnClassCount: Int,
    totalReachableClassCount: Int,
    totalWastedClassCount: Int,
    reachableClassPercent: Double
)

final case class StrictDepsCompileWasteEdge(
    moduleName: String,
    dependencyModuleName: String,
    relationship: String,
    introducedByModuleNames: Seq[String],
    deltaSourceCount: Int,
    reachableDeltaSourceCount: Int,
    wastedDeltaSourceCount: Int,
    wastedDeltaSourcePercent: Double,
    ownSourceCount: Int,
    reachableSourceCount: Int,
    wastedOwnSourceCount: Int,
    reachableSourcePercent: Double,
    ownClassCount: Int,
    reachableClassCount: Int,
    wastedClassCount: Int,
    reachableClassPercent: Double
)

final case class StrictDepsCommonAncestorReport(
    rootModuleCount: Int,
    moduleCount: Int,
    commonAncestorCount: Int,
    ancestors: Seq[StrictDepsCommonAncestor]
)

object StrictDepsCommonAncestorReport {
  val empty: StrictDepsCommonAncestorReport = StrictDepsCommonAncestorReport(
    rootModuleCount = 0,
    moduleCount = 0,
    commonAncestorCount = 0,
    ancestors = Seq.empty
  )
}

final case class StrictDepsCommonAncestor(
    moduleName: String,
    neededByModuleCount: Int,
    comparableModuleCount: Int,
    coveragePercent: Double,
    compileDepth: Int,
    ownSourceCount: Int,
    directDependencyModuleCount: Int,
    ownSourceLineCount: Int = 0,
    ownClassCount: Int = 0
) {
  def isCommonAncestor: Boolean = {
    comparableModuleCount > 0 && neededByModuleCount == comparableModuleCount
  }
}

final case class StrictDepsModuleUsageWeight(
    moduleName: String,
    declaredDirect: Boolean,
    usedClasses: Seq[String],
    dependencyClassCount: Int,
    currentModuleUsagePercent: Double,
    dependencyTouchedPercent: Double
) {
  def usedClassCount: Int = usedClasses.size
}

final case class StrictDepsReachabilityReport(
    providedClassCount: Int,
    directUsedClassCount: Int,
    reachableClassCount: Int,
    unusedClassCount: Int,
    reachableClassPercent: Double,
    providedSourceCount: Int,
    directUsedSourceCount: Int,
    reachableSourceCount: Int,
    unusedSourceCount: Int,
    reachableSourcePercent: Double,
    modules: Seq[StrictDepsModuleReachability]
)

object StrictDepsReachabilityReport {
  val empty: StrictDepsReachabilityReport = StrictDepsReachabilityReport(
    providedClassCount = 0,
    directUsedClassCount = 0,
    reachableClassCount = 0,
    unusedClassCount = 0,
    reachableClassPercent = 0.0,
    providedSourceCount = 0,
    directUsedSourceCount = 0,
    reachableSourceCount = 0,
    unusedSourceCount = 0,
    reachableSourcePercent = 0.0,
    modules = Seq.empty
  )
}

final case class StrictDepsModuleReachability(
    moduleName: String,
    declaredDirect: Boolean,
    providedClasses: Seq[String],
    directUsedClasses: Seq[String],
    reachableClasses: Seq[String],
    unusedClasses: Seq[String],
    providedSources: Seq[String],
    directUsedSources: Seq[String],
    reachableSources: Seq[String],
    unusedSources: Seq[String],
    reachableClassPercent: Double,
    reachableSourcePercent: Double
) {
  def providedClassCount: Int = providedClasses.size
  def directUsedClassCount: Int = directUsedClasses.size
  def reachableClassCount: Int = reachableClasses.size
  def unusedClassCount: Int = unusedClasses.size
  def providedSourceCount: Int = providedSources.size
  def directUsedSourceCount: Int = directUsedSources.size
  def reachableSourceCount: Int = reachableSources.size
  def unusedSourceCount: Int = unusedSources.size
}

final case class StrictDepsWhoIntroducesReport(
    target: String,
    introducers: Seq[StrictDepsIntroducerPath]
)

final case class StrictDepsIntroducerPath(
    directModuleName: String,
    path: Seq[String]
)
