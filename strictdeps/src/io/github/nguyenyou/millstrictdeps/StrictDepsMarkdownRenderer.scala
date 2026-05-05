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
}

