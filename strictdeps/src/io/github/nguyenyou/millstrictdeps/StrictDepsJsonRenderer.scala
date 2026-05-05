package io.github.nguyenyou.millstrictdeps

import ujson.*

object StrictDepsJsonRenderer {

  def render(moduleName: String, report: StrictDepsReport): String = {
    write(toJson(moduleName, report), indent = 2) + "\n"
  }

  private def toJson(moduleName: String, report: StrictDepsReport): Value = {
    Obj(
      "schemaVersion" -> Num(1),
      "moduleName" -> Str(moduleName),
      "hasProblems" -> Bool(report.hasProblems),
      "summary" -> Obj(
        "usedDirectModuleDeps" -> Num(report.usedDirectModuleDeps.size),
        "unusedDirectModuleDeps" -> Num(report.unusedDirectModuleDeps.size),
        "missingDirectModuleDeps" -> Num(report.missingDirectModuleDeps.size),
        "usedLibraryClasspathEntries" -> Num(report.usedLibraryClasspathEntries.size)
      ),
      "usedDirectModuleDeps" -> Arr.from(report.usedDirectModuleDeps.map(usageJson)),
      "unusedDirectModuleDeps" -> stringArray(report.unusedDirectModuleDeps),
      "missingDirectModuleDeps" -> Arr.from(report.missingDirectModuleDeps.map(usageJson)),
      "dependencyUsageWeights" -> Arr.from(report.dependencyUsageWeights.map(weightJson)),
      "usedLibraryClasspathEntries" -> stringArray(report.usedLibraryClasspathEntries)
    )
  }

  private def usageJson(usage: StrictDepsModuleUsage): Value = {
    Obj(
      "moduleName" -> Str(usage.moduleName),
      "usedClassCount" -> Num(usage.usedClassCount),
      "usedClasses" -> stringArray(usage.usedClasses)
    )
  }

  private def weightJson(weight: StrictDepsModuleUsageWeight): Value = {
    Obj(
      "moduleName" -> Str(weight.moduleName),
      "declaredDirect" -> Bool(weight.declaredDirect),
      "usedClassCount" -> Num(weight.usedClassCount),
      "dependencyClassCount" -> Num(weight.dependencyClassCount),
      "currentModuleUsagePercent" -> Num(weight.currentModuleUsagePercent),
      "dependencyTouchedPercent" -> Num(weight.dependencyTouchedPercent),
      "usedClasses" -> stringArray(weight.usedClasses)
    )
  }

  private def stringArray(values: Seq[String]): Arr = {
    Arr.from(values.map(value => Str(value)))
  }
}
