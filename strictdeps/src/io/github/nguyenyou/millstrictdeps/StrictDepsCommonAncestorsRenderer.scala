package io.github.nguyenyou.millstrictdeps

object StrictDepsCommonAncestorsRenderer {
  private val MetricHeader = "metric"
  private val CountHeader = "count"
  private val ModuleHeader = "module"
  private val NeededByHeader = "needed by"
  private val ComparableHeader = "comparable"
  private val CoverageHeader = "coverage"
  private val DepthHeader = "depth"
  private val OwnWeightHeader = "own weight"
  private val DirectDepsHeader = "direct deps"

  def render(
      report: StrictDepsCommonAncestorReport,
      limit: Int
  ): String = {
    val builder = new StringBuilder
    appendSummary(builder, report)

    if (report.ancestors.isEmpty) {
      builder.append("No module dependency graph snapshots were collected.\n")
    } else {
      val rows =
        if (limit <= 0) {
          report.ancestors
        } else {
          report.ancestors.take(limit)
        }
      appendAncestorTable(builder, rows)

      if (limit > 0 && report.ancestors.size > limit) {
        builder.append(s"... ${report.ancestors.size - limit} more modules\n")
      }
    }

    builder.result()
  }

  private def appendSummary(
      builder: StringBuilder,
      report: StrictDepsCommonAncestorReport
  ): Unit = {
    val rows = Seq(
      "root modules" -> report.rootModuleCount.toString,
      "modules analyzed" -> report.moduleCount.toString,
      "common ancestors" -> report.commonAncestorCount.toString
    )
    val labelWidth = maxWidth(MetricHeader +: rows.map { case (label, _) => label })
    val countWidth = maxWidth(CountHeader +: rows.map { case (_, count) => count })

    builder.append(padRight(MetricHeader, labelWidth))
    builder.append("  ")
    builder.append(padLeft(CountHeader, countWidth))
    builder.append("\n")
    builder.append("-" * labelWidth)
    builder.append("  ")
    builder.append("-" * countWidth)
    builder.append("\n")

    rows.foreach { case (label, count) =>
      builder.append(padRight(label, labelWidth))
      builder.append("  ")
      builder.append(padLeft(count, countWidth))
      builder.append("\n")
    }
    builder.append("\n")
  }

  private def appendAncestorTable(
      builder: StringBuilder,
      ancestors: Seq[StrictDepsCommonAncestor]
  ): Unit = {
    val neededByValues = ancestors.map(_.neededByModuleCount.toString)
    val comparableValues = ancestors.map(_.comparableModuleCount.toString)
    val coverageValues = ancestors.map(ancestor => formatPercent(ancestor.coveragePercent))
    val depthValues = ancestors.map(_.compileDepth.toString)
    val ownWeightValues = ancestors.map(_.ownSourceCount.toString)
    val directDepsValues = ancestors.map(_.directDependencyModuleCount.toString)
    val moduleWidth = maxWidth(ModuleHeader +: ancestors.map(ancestor => display(ancestor.moduleName)))
    val neededByWidth = maxWidth(NeededByHeader +: neededByValues)
    val comparableWidth = maxWidth(ComparableHeader +: comparableValues)
    val coverageWidth = maxWidth(CoverageHeader +: coverageValues)
    val depthWidth = maxWidth(DepthHeader +: depthValues)
    val ownWeightWidth = maxWidth(OwnWeightHeader +: ownWeightValues)
    val directDepsWidth = maxWidth(DirectDepsHeader +: directDepsValues)

    appendRow(
      builder = builder,
      moduleWidth = moduleWidth,
      neededByWidth = neededByWidth,
      comparableWidth = comparableWidth,
      coverageWidth = coverageWidth,
      depthWidth = depthWidth,
      ownWeightWidth = ownWeightWidth,
      directDepsWidth = directDepsWidth,
      moduleValue = ModuleHeader,
      neededByValue = NeededByHeader,
      comparableValue = ComparableHeader,
      coverageValue = CoverageHeader,
      depthValue = DepthHeader,
      ownWeightValue = OwnWeightHeader,
      directDepsValue = DirectDepsHeader
    )
    appendRow(
      builder = builder,
      moduleWidth = moduleWidth,
      neededByWidth = neededByWidth,
      comparableWidth = comparableWidth,
      coverageWidth = coverageWidth,
      depthWidth = depthWidth,
      ownWeightWidth = ownWeightWidth,
      directDepsWidth = directDepsWidth,
      moduleValue = "-" * moduleWidth,
      neededByValue = "-" * neededByWidth,
      comparableValue = "-" * comparableWidth,
      coverageValue = "-" * coverageWidth,
      depthValue = "-" * depthWidth,
      ownWeightValue = "-" * ownWeightWidth,
      directDepsValue = "-" * directDepsWidth
    )

    ancestors
      .zip(neededByValues)
      .zip(comparableValues)
      .zip(coverageValues)
      .zip(depthValues)
      .zip(ownWeightValues)
      .zip(directDepsValues)
      .foreach { case ((((((ancestor, neededBy), comparable), coverage), depth), ownWeight), directDeps) =>
        appendRow(
          builder = builder,
          moduleWidth = moduleWidth,
          neededByWidth = neededByWidth,
          comparableWidth = comparableWidth,
          coverageWidth = coverageWidth,
          depthWidth = depthWidth,
          ownWeightWidth = ownWeightWidth,
          directDepsWidth = directDepsWidth,
          moduleValue = display(ancestor.moduleName),
          neededByValue = neededBy,
          comparableValue = comparable,
          coverageValue = coverage,
          depthValue = depth,
          ownWeightValue = ownWeight,
          directDepsValue = directDeps
        )
      }
  }

  private def appendRow(
      builder: StringBuilder,
      moduleWidth: Int,
      neededByWidth: Int,
      comparableWidth: Int,
      coverageWidth: Int,
      depthWidth: Int,
      ownWeightWidth: Int,
      directDepsWidth: Int,
      moduleValue: String,
      neededByValue: String,
      comparableValue: String,
      coverageValue: String,
      depthValue: String,
      ownWeightValue: String,
      directDepsValue: String
  ): Unit = {
    val row = new StringBuilder
    row.append(padRight(moduleValue, moduleWidth))
    row.append("  ")
    row.append(padLeft(neededByValue, neededByWidth))
    row.append("  ")
    row.append(padLeft(comparableValue, comparableWidth))
    row.append("  ")
    row.append(padLeft(coverageValue, coverageWidth))
    row.append("  ")
    row.append(padLeft(depthValue, depthWidth))
    row.append("  ")
    row.append(padLeft(ownWeightValue, ownWeightWidth))
    row.append("  ")
    row.append(padLeft(directDepsValue, directDepsWidth))
    builder.append(trimRight(row.result()))
    builder.append("\n")
  }

  private def formatPercent(value: Double): String = {
    f"$value%.1f%%"
  }

  private def maxWidth(values: Seq[String]): Int = {
    values.map(_.length).max
  }

  private def padRight(value: String, width: Int): String = {
    value + (" " * (width - value.length))
  }

  private def padLeft(value: String, width: Int): String = {
    (" " * (width - value.length)) + value
  }

  private def trimRight(value: String): String = {
    value.reverse.dropWhile(_.isWhitespace).reverse
  }

  private def display(value: String): String = {
    value
      .replace("\r", " ")
      .replace("\n", " ")
  }
}
