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

    renderReachability(builder, report.reachability, maxClassesPerModule)
    renderDependencyWeights(builder, report.dependencyWeights, maxClassesPerModule)
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

  private def renderReachability(
      builder: StringBuilder,
      reachability: StrictDepsReachabilityReport,
      maxItemsPerModule: Int
  ): Unit = {
    builder.append("## Classpath Reachability\n\n")
    builder.append(
      "This graph starts at dependency classes directly used by this module, then follows Zinc class dependencies through transitive compile module deps.\n\n"
    )

    builder.append("| metric | classes | sources |\n")
    builder.append("| --- | ---: | ---: |\n")
    builder.append(
      s"| provided by dependency modules | ${reachability.providedClassCount} | ${reachability.providedSourceCount} |\n"
    )
    builder.append(
      s"| directly used roots | ${reachability.directUsedClassCount} | ${reachability.directUsedSourceCount} |\n"
    )
    builder.append(
      s"| reachable needed | ${reachability.reachableClassCount} (${formatPercent(reachability.reachableClassPercent)}) | ${reachability.reachableSourceCount} (${formatPercent(reachability.reachableSourcePercent)}) |\n"
    )
    builder.append(
      s"| not reached | ${reachability.unusedClassCount} | ${reachability.unusedSourceCount} |\n\n"
    )

    if (reachability.modules.isEmpty) {
      builder.append("_No dependency module classes recorded by Zinc._\n\n")
    } else {
      builder.append(
        "| module | relationship | reachable classes | reachable sources | not reached sources | sample direct roots | sample not reached sources |\n"
      )
      builder.append("| --- | --- | ---: | ---: | ---: | --- | --- |\n")
      reachability.modules.foreach { module =>
        val relationship =
          if (module.declaredDirect) {
            "direct"
          } else {
            "transitive"
          }
        val reachableClasses =
          s"${module.reachableClassCount} / ${module.providedClassCount} (${formatPercent(module.reachableClassPercent)})"
        val reachableSources =
          s"${module.reachableSourceCount} / ${module.providedSourceCount} (${formatPercent(module.reachableSourcePercent)})"
        builder.append(
          s"| `${escape(module.moduleName)}` | $relationship | $reachableClasses | $reachableSources | ${module.unusedSourceCount} | "
        )
        builder.append(sampleValues(module.directUsedClasses, maxItemsPerModule))
        builder.append(" | ")
        builder.append(sampleValues(module.unusedSources, maxItemsPerModule))
        builder.append(" |\n")
      }
      builder.append("\n")
    }
  }

  private def renderDependencyWeights(
      builder: StringBuilder,
      weights: Seq[StrictDepsModuleDependencyWeight],
      maxModulesPerRow: Int
  ): Unit = {
    builder.append("## Dependency Source Weight\n\n")
    builder.append(
      "Absolute sources count a module plus its transitive compile module deps. " +
        "Delta sources count files saved by removing a direct edge, or newly added by declaring a transitive module directly.\n\n"
    )

    if (weights.isEmpty) {
      builder.append("_No dependency module sources recorded by Zinc._\n\n")
    } else {
      builder.append(
        "| module | relationship | own sources | absolute sources | delta sources | transitive modules | direct deps |\n"
      )
      builder.append("| --- | --- | ---: | ---: | ---: | ---: | --- |\n")
      weights.foreach { weight =>
        val relationship =
          if (weight.declaredDirect) {
            "direct"
          } else {
            "transitive"
          }
        val directDeps = sampleValues(weight.directDependencyModuleNames, maxModulesPerRow)
        builder.append(
          s"| `${escape(weight.moduleName)}` | $relationship | ${weight.ownSourceCount} | " +
            s"${weight.absoluteSourceCount} | ${weight.deltaSourceCount} (${weight.deltaKind}) | " +
            s"${weight.transitiveDependencyModuleCount} | $directDeps |\n"
        )
      }
      builder.append("\n")
    }
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
        val sample = sampleValues(weight.usedClasses, maxClassesPerModule)
        builder.append(
          s"| `${escape(weight.moduleName)}` | $relationship | ${weight.usedClassCount} | ${formatPercent(weight.currentModuleUsagePercent)} | $touched | $sample |\n"
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
        builder.append(
          s"| `${escape(usage.moduleName)}` | ${usage.usedClassCount} | ${sampleValues(usage.usedClasses, maxClassesPerModule)} |\n"
        )
      }
      builder.append("\n")
    }
  }

  private def sampleValues(values: Seq[String], maxValues: Int): String = {
    if (values.isEmpty) {
      ""
    } else {
      val sample = values
        .take(maxValues)
        .map(value => s"`${escape(value)}`")
        .mkString("<br>")
      val suffix =
        if (values.size > maxValues) {
          s"<br>... ${values.size - maxValues} more"
        } else {
          ""
        }
      sample + suffix
    }
  }

  private def escape(value: String): String = {
    value.replace("|", "\\|")
  }

  private def formatPercent(value: Double): String = {
    f"$value%.1f%%"
  }
}
