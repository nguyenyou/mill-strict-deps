package io.github.nguyenyou.millstrictdeps

final case class StrictDepsReport(
    usedDirectModuleDeps: Seq[StrictDepsModuleUsage],
    unusedDirectModuleDeps: Seq[String],
    missingDirectModuleDeps: Seq[StrictDepsModuleUsage],
    usedLibraryClasspathEntries: Seq[String]
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

