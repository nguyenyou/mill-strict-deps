package io.github.nguyenyou.millstrictdeps

final case class StrictDepsReport(
    usedDirectModuleDeps: Seq[StrictDepsModuleUsage],
    unusedDirectModuleDeps: Seq[String],
    missingDirectModuleDeps: Seq[StrictDepsModuleUsage],
    usedLibraryClasspathEntries: Seq[String],
    dependencyUsageWeights: Seq[StrictDepsModuleUsageWeight] = Seq.empty,
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
    analysisFile: os.Path
)

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
