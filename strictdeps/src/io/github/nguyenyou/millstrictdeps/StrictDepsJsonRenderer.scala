package io.github.nguyenyou.millstrictdeps

import ujson.*

object StrictDepsJsonRenderer {

  def render(moduleName: String, report: StrictDepsReport): String = {
    write(toJson(moduleName, report), indent = 2) + "\n"
  }

  private def toJson(moduleName: String, report: StrictDepsReport): Value = {
    Obj(
      "schemaVersion" -> Num(3),
      "moduleName" -> Str(moduleName),
      "hasProblems" -> Bool(report.hasProblems),
      "summary" -> Obj(
        "usedDirectModuleDeps" -> Num(report.usedDirectModuleDeps.size),
        "unusedDirectModuleDeps" -> Num(report.unusedDirectModuleDeps.size),
        "missingDirectModuleDeps" -> Num(report.missingDirectModuleDeps.size),
        "usedLibraryClasspathEntries" -> Num(report.usedLibraryClasspathEntries.size),
        "providedDependencyClasses" -> Num(report.reachability.providedClassCount),
        "directlyUsedDependencyClasses" -> Num(report.reachability.directUsedClassCount),
        "reachableDependencyClasses" -> Num(report.reachability.reachableClassCount),
        "unusedDependencyClasses" -> Num(report.reachability.unusedClassCount),
        "providedDependencySources" -> Num(report.reachability.providedSourceCount),
        "directlyUsedDependencySources" -> Num(report.reachability.directUsedSourceCount),
        "reachableDependencySources" -> Num(report.reachability.reachableSourceCount),
        "unusedDependencySources" -> Num(report.reachability.unusedSourceCount),
        "dependencyWeightModules" -> Num(report.dependencyWeights.size)
      ),
      "usedDirectModuleDeps" -> Arr.from(report.usedDirectModuleDeps.map(usageJson)),
      "unusedDirectModuleDeps" -> stringArray(report.unusedDirectModuleDeps),
      "missingDirectModuleDeps" -> Arr.from(report.missingDirectModuleDeps.map(usageJson)),
      "dependencyUsageWeights" -> Arr.from(report.dependencyUsageWeights.map(weightJson)),
      "dependencyWeights" -> Arr.from(report.dependencyWeights.map(dependencyWeightJson)),
      "reachability" -> reachabilityJson(report.reachability),
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

  private def dependencyWeightJson(weight: StrictDepsModuleDependencyWeight): Value = {
    Obj(
      "moduleName" -> Str(weight.moduleName),
      "declaredDirect" -> Bool(weight.declaredDirect),
      "directDependencyModuleCount" -> Num(weight.directDependencyModuleCount),
      "transitiveDependencyModuleCount" -> Num(weight.transitiveDependencyModuleCount),
      "absoluteModuleCount" -> Num(weight.absoluteModuleCount),
      "ownSourceCount" -> Num(weight.ownSourceCount),
      "absoluteSourceCount" -> Num(weight.absoluteSourceCount),
      "deltaSourceCount" -> Num(weight.deltaSourceCount),
      "ownSourceLineCount" -> Num(weight.ownSourceLineCount),
      "absoluteSourceLineCount" -> Num(weight.absoluteSourceLineCount),
      "deltaSourceLineCount" -> Num(weight.deltaSourceLineCount),
      "ownClassCount" -> Num(weight.ownClassCount),
      "absoluteClassCount" -> Num(weight.absoluteClassCount),
      "deltaKind" -> Str(weight.deltaKind),
      "directDependencyModuleNames" -> stringArray(weight.directDependencyModuleNames),
      "transitiveDependencyModuleNames" -> stringArray(weight.transitiveDependencyModuleNames)
    )
  }

  private def reachabilityJson(reachability: StrictDepsReachabilityReport): Value = {
    Obj(
      "providedClassCount" -> Num(reachability.providedClassCount),
      "directUsedClassCount" -> Num(reachability.directUsedClassCount),
      "reachableClassCount" -> Num(reachability.reachableClassCount),
      "unusedClassCount" -> Num(reachability.unusedClassCount),
      "reachableClassPercent" -> Num(reachability.reachableClassPercent),
      "providedSourceCount" -> Num(reachability.providedSourceCount),
      "directUsedSourceCount" -> Num(reachability.directUsedSourceCount),
      "reachableSourceCount" -> Num(reachability.reachableSourceCount),
      "unusedSourceCount" -> Num(reachability.unusedSourceCount),
      "reachableSourcePercent" -> Num(reachability.reachableSourcePercent),
      "modules" -> Arr.from(reachability.modules.map(moduleReachabilityJson))
    )
  }

  private def moduleReachabilityJson(module: StrictDepsModuleReachability): Value = {
    Obj(
      "moduleName" -> Str(module.moduleName),
      "declaredDirect" -> Bool(module.declaredDirect),
      "providedClassCount" -> Num(module.providedClassCount),
      "directUsedClassCount" -> Num(module.directUsedClassCount),
      "reachableClassCount" -> Num(module.reachableClassCount),
      "unusedClassCount" -> Num(module.unusedClassCount),
      "reachableClassPercent" -> Num(module.reachableClassPercent),
      "providedSourceCount" -> Num(module.providedSourceCount),
      "directUsedSourceCount" -> Num(module.directUsedSourceCount),
      "reachableSourceCount" -> Num(module.reachableSourceCount),
      "unusedSourceCount" -> Num(module.unusedSourceCount),
      "reachableSourcePercent" -> Num(module.reachableSourcePercent),
      "providedClasses" -> stringArray(module.providedClasses),
      "directUsedClasses" -> stringArray(module.directUsedClasses),
      "reachableClasses" -> stringArray(module.reachableClasses),
      "unusedClasses" -> stringArray(module.unusedClasses),
      "providedSources" -> stringArray(module.providedSources),
      "directUsedSources" -> stringArray(module.directUsedSources),
      "reachableSources" -> stringArray(module.reachableSources),
      "unusedSources" -> stringArray(module.unusedSources)
    )
  }

  private def stringArray(values: Seq[String]): Arr = {
    Arr.from(values.map(value => Str(value)))
  }
}
