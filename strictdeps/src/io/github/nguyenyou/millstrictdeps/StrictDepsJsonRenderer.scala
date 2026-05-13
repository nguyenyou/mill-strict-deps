package io.github.nguyenyou.millstrictdeps

import ujson.*

object StrictDepsJsonRenderer {

  def render(
      moduleName: String,
      report: StrictDepsReport,
      weightReport: Option[StrictDepsWeightReport] = None,
      compileWaste: Option[StrictDepsCompileWasteSnapshot] = None
  ): String = {
    write(toJson(moduleName, report, weightReport, compileWaste), indent = 2) + "\n"
  }

  private def toJson(
      moduleName: String,
      report: StrictDepsReport,
      weightReport: Option[StrictDepsWeightReport],
      compileWaste: Option[StrictDepsCompileWasteSnapshot]
  ): Value = {
    val json = Obj(
      "schemaVersion" -> Num(4),
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

    weightReport.foreach { report =>
      json("weightReport") = weightReportJson(report)
    }
    compileWaste.foreach { snapshot =>
      json("compileWaste") = compileWasteJson(snapshot)
    }

    json
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

  private def weightReportJson(report: StrictDepsWeightReport): Value = {
    Obj(
      "currentModuleSources" -> sourceComparisonJson(report.currentModuleSources),
      "dependencySources" -> sourceComparisonJson(report.dependencySources),
      "totalSources" -> sourceComparisonJson(report.totalSources),
      "currentModuleSourceLines" -> sourceComparisonJson(report.currentModuleSourceLines),
      "dependencySourceLines" -> sourceComparisonJson(report.dependencySourceLines),
      "totalSourceLines" -> sourceComparisonJson(report.totalSourceLines),
      "currentModuleClassCount" -> Num(report.currentModuleClassCount),
      "dependencyClassCount" -> Num(report.dependencyClassCount),
      "totalClassCount" -> Num(report.totalClassCount),
      "dependencyWeights" -> Arr.from(report.dependencyWeights.map(moduleWeightComparisonJson)),
      "compileDepths" -> Arr.from(report.compileDepths.map(compileDepthJson)),
      "targetDepthIndex" -> Num(report.targetDepthIndex),
      "reachability" -> reachabilityJson(report.reachability)
    )
  }

  private def sourceComparisonJson(comparison: StrictDepsSourceWeightComparison): Value = {
    Obj(
      "millSourceCount" -> Num(comparison.millSourceCount),
      "zincSourceCount" -> Num(comparison.zincSourceCount),
      "matches" -> Bool(comparison.matches),
      "maxSourceCount" -> Num(comparison.maxSourceCount)
    )
  }

  private def moduleWeightComparisonJson(weight: StrictDepsModuleWeightComparison): Value = {
    Obj(
      "moduleName" -> Str(weight.moduleName),
      "declaredDirect" -> Bool(weight.declaredDirect),
      "relationship" -> Str(if (weight.declaredDirect) "direct" else "transitive"),
      "ownSources" -> sourceComparisonJson(weight.ownSources),
      "absoluteSources" -> sourceComparisonJson(weight.absoluteSources),
      "deltaSources" -> sourceComparisonJson(weight.deltaSources),
      "compileDepthDeltaSources" -> sourceComparisonJson(weight.compileDepthDeltaSources),
      "ownSourceLines" -> sourceComparisonJson(weight.ownSourceLines),
      "absoluteSourceLines" -> sourceComparisonJson(weight.absoluteSourceLines),
      "deltaSourceLines" -> sourceComparisonJson(weight.deltaSourceLines),
      "compileDepthDeltaSourceLines" -> sourceComparisonJson(weight.compileDepthDeltaSourceLines),
      "ownClassCount" -> Num(weight.ownClassCount),
      "absoluteClassCount" -> Num(weight.absoluteClassCount),
      "usedClassCount" -> Num(weight.usedClassCount),
      "usedClassTotalCount" -> Num(weight.usedClassTotalCount),
      "usedClassPercent" -> Num(weight.usedClassPercent),
      "reachableClassCount" -> Num(weight.reachableClassCount),
      "reachableClassTotalCount" -> Num(weight.reachableClassTotalCount),
      "reachableClassPercent" -> Num(weight.reachableClassPercent),
      "reachableSourceCount" -> Num(weight.reachableSourceCount),
      "reachableSourceTotalCount" -> Num(weight.reachableSourceTotalCount),
      "reachableSourcePercent" -> Num(weight.reachableSourcePercent),
      "reachableDeltaSourceCount" -> Num(weight.reachableDeltaSourceCount),
      "wastedDeltaSourceCount" -> Num(weight.wastedDeltaSourceCount),
      "wastedDeltaSourcePercent" -> Num(weight.wastedDeltaSourcePercent),
      "wastedOwnSourceCount" -> Num(weight.wastedOwnSourceCount),
      "wastedClassCount" -> Num(weight.wastedClassCount),
      "introducedByModuleNames" -> stringArray(weight.introducedByModuleNames)
    )
  }

  private def compileDepthJson(depth: StrictDepsCompileDepth): Value = {
    Obj(
      "index" -> Num(depth.index),
      "moduleCount" -> Num(depth.modules.size),
      "modules" -> Arr.from(depth.modules.map(moduleWeightComparisonJson))
    )
  }

  private def compileWasteJson(snapshot: StrictDepsCompileWasteSnapshot): Value = {
    Obj(
      "moduleName" -> Str(snapshot.moduleName),
      "dependencySourceCount" -> Num(snapshot.dependencySourceCount),
      "reachableDependencySourceCount" -> Num(snapshot.reachableDependencySourceCount),
      "wastedDependencySourceCount" -> Num(snapshot.wastedDependencySourceCount),
      "dependencyClassCount" -> Num(snapshot.dependencyClassCount),
      "reachableDependencyClassCount" -> Num(snapshot.reachableDependencyClassCount),
      "wastedDependencyClassCount" -> Num(snapshot.wastedDependencyClassCount),
      "deltaSourceCount" -> Num(snapshot.deltaSourceCount),
      "reachableDeltaSourceCount" -> Num(snapshot.reachableDeltaSourceCount),
      "wastedDeltaSourceCount" -> Num(snapshot.wastedDeltaSourceCount),
      "dependencies" -> Arr.from(snapshot.dependencies.map(compileWasteDependencyJson))
    )
  }

  private def compileWasteDependencyJson(dependency: StrictDepsCompileWasteDependency): Value = {
    Obj(
      "moduleName" -> Str(dependency.moduleName),
      "declaredDirect" -> Bool(dependency.declaredDirect),
      "relationship" -> Str(dependency.relationship),
      "introducedByModuleNames" -> stringArray(dependency.introducedByModuleNames),
      "ownSourceCount" -> Num(dependency.ownSourceCount),
      "reachableSourceCount" -> Num(dependency.reachableSourceCount),
      "wastedOwnSourceCount" -> Num(dependency.wastedOwnSourceCount),
      "reachableSourcePercent" -> Num(dependency.reachableSourcePercent),
      "deltaSourceCount" -> Num(dependency.deltaSourceCount),
      "reachableDeltaSourceCount" -> Num(dependency.reachableDeltaSourceCount),
      "wastedDeltaSourceCount" -> Num(dependency.wastedDeltaSourceCount),
      "wastedDeltaSourcePercent" -> Num(dependency.wastedDeltaSourcePercent),
      "ownClassCount" -> Num(dependency.ownClassCount),
      "reachableClassCount" -> Num(dependency.reachableClassCount),
      "wastedClassCount" -> Num(dependency.wastedClassCount),
      "reachableClassPercent" -> Num(dependency.reachableClassPercent)
    )
  }

  private def stringArray(values: Seq[String]): Arr = {
    Arr.from(values.map(value => Str(value)))
  }
}
