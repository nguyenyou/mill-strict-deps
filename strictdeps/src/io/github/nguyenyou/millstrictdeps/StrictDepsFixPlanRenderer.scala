package io.github.nguyenyou.millstrictdeps

object StrictDepsFixPlanRenderer {

  def render(
      moduleName: String,
      report: StrictDepsReport,
      maxClassesPerModule: Int
  ): String = {
    val builder = new StringBuilder
    builder.append(s"# Strict Deps Fix Plan: ${escape(moduleName)}\n\n")
    builder.append(
      "This is a suggested edit plan. It does not mutate `build.mill`.\n\n"
    )
    val weightsByModule = report.dependencyWeights.map(weight => weight.moduleName -> weight).toMap

    renderAdditions(
      builder,
      moduleName,
      report.missingDirectModuleDeps,
      maxClassesPerModule,
      weightsByModule
    )
    renderRemovals(
      builder,
      moduleName,
      report.unusedDirectModuleDeps,
      weightsByModule
    )

    if (!report.hasProblems) {
      builder.append("## Result\n\n")
      builder.append("_No direct module dependency edits suggested._\n")
    }

    builder.result()
  }

  private def renderAdditions(
      builder: StringBuilder,
      moduleName: String,
      usages: Seq[StrictDepsModuleUsage],
      maxClassesPerModule: Int,
      weightsByModule: Map[String, StrictDepsModuleDependencyWeight]
  ): Unit = {
    builder.append("## Add Direct Module Deps\n\n")
    if (usages.isEmpty) {
      builder.append("_None._\n\n")
    } else {
      usages.foreach { usage =>
        val sample = usage.usedClasses
          .take(maxClassesPerModule)
          .map(className => s"`$className`")
          .mkString(", ")
        val suffix =
          if (usage.usedClasses.size > maxClassesPerModule) {
            s", ... ${usage.usedClasses.size - maxClassesPerModule} more"
          } else {
            ""
          }
        builder.append(
          s"- Add `${escape(usage.moduleName)}` to `${escape(moduleName)}` because it provides "
        )
        builder.append(s"$sample$suffix")
        appendWeight(builder, weightsByModule.get(usage.moduleName))
        builder.append(".\n")
      }
      builder.append("\n")
    }
  }

  private def renderRemovals(
      builder: StringBuilder,
      moduleName: String,
      modules: Seq[String],
      weightsByModule: Map[String, StrictDepsModuleDependencyWeight]
  ): Unit = {
    builder.append("## Remove Direct Module Deps\n\n")
    if (modules.isEmpty) {
      builder.append("_None._\n\n")
    } else {
      modules.foreach { moduleNameToRemove =>
        builder.append(
          s"- Remove `${escape(moduleNameToRemove)}` from `${escape(moduleName)}` " +
            "because Zinc recorded no compile-time class use"
        )
        appendWeight(builder, weightsByModule.get(moduleNameToRemove))
        builder.append(".\n")
      }
      builder.append("\n")
    }
  }

  private def appendWeight(
      builder: StringBuilder,
      weight: Option[StrictDepsModuleDependencyWeight]
  ): Unit = {
    weight.foreach { value =>
      builder.append(
        s" (absolute weight: ${formatSourceCount(value.absoluteSourceCount)}, " +
          s"delta weight: ${formatSourceCount(value.deltaSourceCount)} ${value.deltaKind})"
      )
    }
  }

  private def formatSourceCount(count: Int): String = {
    if (count == 1) {
      "1 source"
    } else {
      s"$count sources"
    }
  }

  private def escape(value: String): String = {
    value.replace("|", "\\|")
  }
}
