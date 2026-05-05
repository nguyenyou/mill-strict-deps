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

    renderAdditions(builder, moduleName, report.missingDirectModuleDeps, maxClassesPerModule)
    renderRemovals(builder, moduleName, report.unusedDirectModuleDeps)

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
      maxClassesPerModule: Int
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
        builder.append(s"$sample$suffix.\n")
      }
      builder.append("\n")
    }
  }

  private def renderRemovals(
      builder: StringBuilder,
      moduleName: String,
      modules: Seq[String]
  ): Unit = {
    builder.append("## Remove Direct Module Deps\n\n")
    if (modules.isEmpty) {
      builder.append("_None._\n\n")
    } else {
      modules.foreach { moduleNameToRemove =>
        builder.append(
          s"- Remove `${escape(moduleNameToRemove)}` from `${escape(moduleName)}` because Zinc recorded no compile-time class use.\n"
        )
      }
      builder.append("\n")
    }
  }

  private def escape(value: String): String = {
    value.replace("|", "\\|")
  }
}
