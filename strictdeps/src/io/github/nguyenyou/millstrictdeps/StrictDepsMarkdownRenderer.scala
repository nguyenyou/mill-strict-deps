package io.github.nguyenyou.millstrictdeps

object StrictDepsMarkdownRenderer {

  def render(
      moduleName: String,
      report: StrictDepsReport,
      maxClassesPerModule: Int
  ): String = {
    val builder = new StringBuilder
    builder.append(s"# Strict Deps Report: ${escape(moduleName)}\n\n")
    builder.append("## Summary\n\n")
    builder.append("| metric | count |\n")
    builder.append("| --- | ---: |\n")
    builder.append(s"| used direct module deps | ${report.usedDirectModuleDeps.size} |\n")
    builder.append(s"| unused direct module deps | ${report.unusedDirectModuleDeps.size} |\n")
    builder.append(s"| missing direct module deps | ${report.missingDirectModuleDeps.size} |\n")
    builder.append(s"| used library classpath entries | ${report.usedLibraryClasspathEntries.size} |\n\n")

    renderUsageWeights(builder, report.dependencyUsageWeights, maxClassesPerModule)
    renderUnused(builder, report.unusedDirectModuleDeps)
    renderUsageSection(
      builder = builder,
      title = "Missing Direct Module Deps",
      description =
        "These modules are used through the transitive classpath but are not declared directly.",
      usages = report.missingDirectModuleDeps,
      maxClassesPerModule = maxClassesPerModule
    )
    renderUsageSection(
      builder = builder,
      title = "Used Direct Module Deps",
      description = "These direct module deps contributed classes referenced by this module.",
      usages = report.usedDirectModuleDeps,
      maxClassesPerModule = maxClassesPerModule
    )

    builder.append("## Used Library Classpath Entries\n\n")
    if (report.usedLibraryClasspathEntries.isEmpty) {
      builder.append("_None recorded by Zinc._\n")
    } else {
      report.usedLibraryClasspathEntries.foreach { entry =>
        builder.append(s"- `${escape(entry)}`\n")
      }
    }
    builder.result()
  }

  private def renderUnused(builder: StringBuilder, modules: Seq[String]): Unit = {
    builder.append("## Unused Direct Module Deps\n\n")
    if (modules.isEmpty) {
      builder.append("_None._\n\n")
    } else {
      modules.foreach { moduleName =>
        builder.append(s"- `${escape(moduleName)}`\n")
      }
      builder.append("\n")
    }
  }

  private def renderUsageWeights(
      builder: StringBuilder,
      weights: Seq[StrictDepsModuleUsageWeight],
      maxClassesPerModule: Int
  ): Unit = {
    builder.append("## Dependency Usage Weight\n\n")
    builder.append(
      "These numbers are advisory. They count distinct dependency classes touched by this module.\n\n"
    )

    if (weights.isEmpty) {
      builder.append("_No internal dependency class usage recorded by Zinc._\n\n")
    } else {
      builder.append(
        "| module | relationship | used classes | share of this module's internal usage | dependency classes touched | sample |\n"
      )
      builder.append("| --- | --- | ---: | ---: | ---: | --- |\n")
      weights.foreach { weight =>
        val relationship =
          if (weight.declaredDirect) {
            "direct"
          } else {
            "transitive"
          }
        val touched =
          s"${weight.usedClassCount} / ${weight.dependencyClassCount} (${formatPercent(weight.dependencyTouchedPercent)})"
        val sample = weight.usedClasses
          .take(maxClassesPerModule)
          .map(className => s"`$className`")
          .mkString("<br>")
        val suffix =
          if (weight.usedClasses.size > maxClassesPerModule) {
            s"<br>... ${weight.usedClasses.size - maxClassesPerModule} more"
          } else {
            ""
          }
        builder.append(
          s"| `${escape(weight.moduleName)}` | $relationship | ${weight.usedClassCount} | ${formatPercent(weight.currentModuleUsagePercent)} | $touched | $sample$suffix |\n"
        )
      }
      builder.append("\n")
    }
  }

  private def renderUsageSection(
      builder: StringBuilder,
      title: String,
      description: String,
      usages: Seq[StrictDepsModuleUsage],
      maxClassesPerModule: Int
  ): Unit = {
    builder.append(s"## $title\n\n")
    builder.append(description)
    builder.append("\n\n")

    if (usages.isEmpty) {
      builder.append("_None._\n\n")
    } else {
      builder.append("| module | used classes | sample |\n")
      builder.append("| --- | ---: | --- |\n")
      usages.foreach { usage =>
        val sample = usage.usedClasses
          .take(maxClassesPerModule)
          .map(className => s"`$className`")
          .mkString("<br>")
        val suffix =
          if (usage.usedClasses.size > maxClassesPerModule) {
            s"<br>... ${usage.usedClasses.size - maxClassesPerModule} more"
          } else {
            ""
          }
        builder.append(
          s"| `${escape(usage.moduleName)}` | ${usage.usedClassCount} | $sample$suffix |\n"
        )
      }
      builder.append("\n")
    }
  }

  private def escape(value: String): String = {
    value.replace("|", "\\|")
  }

  private def formatPercent(value: Double): String = {
    f"$value%.1f%%"
  }
}
