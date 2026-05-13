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
  private val OwnLinesHeader = "own lines"
  private val OwnClassesHeader = "own classes"
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
    val rows = ancestors.map(renderedRow)
    val moduleWidth = maxWidth(ModuleHeader +: rows.map(_.module))
    val neededByWidth = maxWidth(NeededByHeader +: rows.map(_.neededBy))
    val comparableWidth = maxWidth(ComparableHeader +: rows.map(_.comparable))
    val coverageWidth = maxWidth(CoverageHeader +: rows.map(_.coverage))
    val depthWidth = maxWidth(DepthHeader +: rows.map(_.depth))
    val ownWeightWidth = maxWidth(OwnWeightHeader +: rows.map(_.ownWeight))
    val ownLinesWidth = maxWidth(OwnLinesHeader +: rows.map(_.ownLines))
    val ownClassesWidth = maxWidth(OwnClassesHeader +: rows.map(_.ownClasses))
    val directDepsWidth = maxWidth(DirectDepsHeader +: rows.map(_.directDeps))

    appendRow(
      builder = builder,
      moduleWidth = moduleWidth,
      neededByWidth = neededByWidth,
      comparableWidth = comparableWidth,
      coverageWidth = coverageWidth,
      depthWidth = depthWidth,
      ownWeightWidth = ownWeightWidth,
      ownLinesWidth = ownLinesWidth,
      ownClassesWidth = ownClassesWidth,
      directDepsWidth = directDepsWidth,
      moduleValue = ModuleHeader,
      neededByValue = NeededByHeader,
      comparableValue = ComparableHeader,
      coverageValue = CoverageHeader,
      depthValue = DepthHeader,
      ownWeightValue = OwnWeightHeader,
      ownLinesValue = OwnLinesHeader,
      ownClassesValue = OwnClassesHeader,
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
      ownLinesWidth = ownLinesWidth,
      ownClassesWidth = ownClassesWidth,
      directDepsWidth = directDepsWidth,
      moduleValue = "-" * moduleWidth,
      neededByValue = "-" * neededByWidth,
      comparableValue = "-" * comparableWidth,
      coverageValue = "-" * coverageWidth,
      depthValue = "-" * depthWidth,
      ownWeightValue = "-" * ownWeightWidth,
      ownLinesValue = "-" * ownLinesWidth,
      ownClassesValue = "-" * ownClassesWidth,
      directDepsValue = "-" * directDepsWidth
    )

    rows.foreach { row =>
      appendRow(
        builder = builder,
        moduleWidth = moduleWidth,
        neededByWidth = neededByWidth,
        comparableWidth = comparableWidth,
        coverageWidth = coverageWidth,
        depthWidth = depthWidth,
        ownWeightWidth = ownWeightWidth,
        ownLinesWidth = ownLinesWidth,
        ownClassesWidth = ownClassesWidth,
        directDepsWidth = directDepsWidth,
        moduleValue = row.module,
        neededByValue = row.neededBy,
        comparableValue = row.comparable,
        coverageValue = row.coverage,
        depthValue = row.depth,
        ownWeightValue = row.ownWeight,
        ownLinesValue = row.ownLines,
        ownClassesValue = row.ownClasses,
        directDepsValue = row.directDeps
      )
    }
  }

  private def renderedRow(ancestor: StrictDepsCommonAncestor): RenderedAncestorRow = {
    RenderedAncestorRow(
      module = display(ancestor.moduleName),
      neededBy = ancestor.neededByModuleCount.toString,
      comparable = ancestor.comparableModuleCount.toString,
      coverage = formatPercent(ancestor.coveragePercent),
      depth = ancestor.compileDepth.toString,
      ownWeight = ancestor.ownSourceCount.toString,
      ownLines = ancestor.ownSourceLineCount.toString,
      ownClasses = ancestor.ownClassCount.toString,
      directDeps = ancestor.directDependencyModuleCount.toString
    )
  }

  private def appendRow(
      builder: StringBuilder,
      moduleWidth: Int,
      neededByWidth: Int,
      comparableWidth: Int,
      coverageWidth: Int,
      depthWidth: Int,
      ownWeightWidth: Int,
      ownLinesWidth: Int,
      ownClassesWidth: Int,
      directDepsWidth: Int,
      moduleValue: String,
      neededByValue: String,
      comparableValue: String,
      coverageValue: String,
      depthValue: String,
      ownWeightValue: String,
      ownLinesValue: String,
      ownClassesValue: String,
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
    row.append(padLeft(ownLinesValue, ownLinesWidth))
    row.append("  ")
    row.append(padLeft(ownClassesValue, ownClassesWidth))
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

  private final case class RenderedAncestorRow(
      module: String,
      neededBy: String,
      comparable: String,
      coverage: String,
      depth: String,
      ownWeight: String,
      ownLines: String,
      ownClasses: String,
      directDeps: String
  )
}
