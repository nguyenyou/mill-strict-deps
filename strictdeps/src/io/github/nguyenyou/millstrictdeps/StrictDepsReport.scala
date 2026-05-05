package io.github.nguyenyou.millstrictdeps

final case class StrictDepsReport(
    usedDirectModuleDeps: Seq[StrictDepsModuleUsage],
    unusedDirectModuleDeps: Seq[String],
    missingDirectModuleDeps: Seq[StrictDepsModuleUsage],
    usedLibraryClasspathEntries: Seq[String],
    dependencyUsageWeights: Seq[StrictDepsModuleUsageWeight] = Seq.empty
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
