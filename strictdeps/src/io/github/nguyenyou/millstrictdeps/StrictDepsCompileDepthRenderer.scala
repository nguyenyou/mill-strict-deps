package io.github.nguyenyou.millstrictdeps

object StrictDepsCompileDepthRenderer {
  private val MetricHeader = "metric"
  private val DepthHeader = "depth"
  private val ModuleHeader = "module"
  private val RelationshipHeader = "relationship"
  private val OwnWeightHeader = "own weight"
  private val AbsoluteWeightHeader = "absolute weight"
  private val DeltaWeightHeader = "delta weight"
  private val OwnLinesHeader = "own lines"
  private val AbsoluteLinesHeader = "absolute lines"
  private val DeltaLinesHeader = "delta lines"
  private val OwnClassesHeader = "own classes"
  private val UsedClassesHeader = "used classes"
  private val AbsoluteClassesHeader = "absolute classes"
  private val SourceCountHeader = "count"
  private val NoteHeader = "note"
  private val TargetRelationship = "target"

  def render(
      moduleName: String,
      report: StrictDepsWeightReport
  ): String = {
    val builder = new StringBuilder
    appendSummary(builder, report)
    appendComparisonNote(builder, report)

    val layout = depthLayout(report.compileDepths, moduleName, report)
    appendDepthHeader(builder, layout)
    report.compileDepths.foreach { depth =>
      appendDepth(builder, depth, layout)
      appendSeparator(builder, layout.tableWidth)
    }
    appendTarget(builder, moduleName, report, layout)
    builder.result()
  }

  private def appendDepthHeader(
      builder: StringBuilder,
      layout: DepthLayout
  ): Unit = {
    appendTableRow(
      builder = builder,
      layout = layout,
      depthValue = DepthHeader,
      moduleValue = ModuleHeader,
      relationshipValue = RelationshipHeader,
      ownValue = OwnWeightHeader,
      absoluteValue = AbsoluteWeightHeader,
      deltaValue = DeltaWeightHeader,
      ownLinesValue = OwnLinesHeader,
      absoluteLinesValue = AbsoluteLinesHeader,
      deltaLinesValue = DeltaLinesHeader,
      ownClassesValue = OwnClassesHeader,
      usedClassesValue = UsedClassesHeader,
      absoluteClassesValue = AbsoluteClassesHeader,
      noteValue = Option.when(layout.showNotes)(NoteHeader).getOrElse("")
    )
  }

  private def appendDepth(
      builder: StringBuilder,
      depth: StrictDepsCompileDepth,
      layout: DepthLayout
  ): Unit = {
    val rows = depth.modules.map(renderedRow)

    rows.zipWithIndex.foreach { case (row, index) =>
      appendTableRow(
        builder = builder,
        layout = layout,
        depthValue = depthCell(depth, index),
        moduleValue = row.moduleName,
        relationshipValue = row.relationship,
        ownValue = row.ownWeight,
        absoluteValue = row.absoluteWeight,
        deltaValue = row.deltaWeight,
        ownLinesValue = row.ownLines,
        absoluteLinesValue = row.absoluteLines,
        deltaLinesValue = row.deltaLines,
        ownClassesValue = row.ownClasses,
        usedClassesValue = row.usedClasses,
        absoluteClassesValue = row.absoluteClasses,
        noteValue = row.note
      )
    }

    if (depth.modules.size <= 1) {
      appendTableRow(
        builder = builder,
        layout = layout,
        depthValue = moduleCountLabel(depth.modules.size),
        moduleValue = "",
        relationshipValue = "",
        ownValue = "",
        absoluteValue = "",
        deltaValue = "",
        ownLinesValue = "",
        absoluteLinesValue = "",
        deltaLinesValue = "",
        ownClassesValue = "",
        usedClassesValue = "",
        absoluteClassesValue = "",
        noteValue = ""
      )
    }
  }

  private def depthCell(
      depth: StrictDepsCompileDepth,
      rowIndex: Int
  ): String = {
    if (rowIndex == 0) {
      s"depth ${depth.index}"
    } else if (rowIndex == 1) {
      moduleCountLabel(depth.modules.size)
    } else {
      ""
    }
  }

  private def depthLayout(
      depths: Seq[StrictDepsCompileDepth],
      moduleName: String,
      report: StrictDepsWeightReport
  ): DepthLayout = {
    val weights = depths.flatMap(_.modules)
    val targetOwnValue = formatComparison(report.currentModuleSources)
    val targetAbsoluteValue = formatComparison(report.totalSources)
    val targetDeltaValue = formatComparison(report.currentModuleSources)
    val targetOwnLinesValue = formatComparison(report.currentModuleSourceLines)
    val targetAbsoluteLinesValue = formatComparison(report.totalSourceLines)
    val targetDeltaLinesValue = formatComparison(report.currentModuleSourceLines)
    val targetOwnClassesValue = report.currentModuleClassCount.toString
    val targetUsedClassesValue = ""
    val targetAbsoluteClassesValue = report.totalClassCount.toString
    val targetNoteValue = targetNote(report)
    val rows = weights.map(renderedRow)
    val notes = rows.map(_.note)
    val depthValues = depths.flatMap { depth =>
      Seq(s"depth ${depth.index}", moduleCountLabel(depth.modules.size))
    } ++ Seq("target", s"depth ${report.targetDepthIndex}")

    DepthLayout(
      depthWidth = maxWidth(DepthHeader +: depthValues),
      moduleWidth = maxWidth(
        ModuleHeader +: (weights.map(weight => display(weight.moduleName)) :+ display(moduleName))
      ),
      relationshipWidth = maxWidth(RelationshipHeader +: (weights.map(relationship) :+ TargetRelationship)),
      ownWeightWidth = maxWidth(OwnWeightHeader +: (rows.map(_.ownWeight) :+ targetOwnValue)),
      absoluteWeightWidth = maxWidth(
        AbsoluteWeightHeader +: (rows.map(_.absoluteWeight) :+ targetAbsoluteValue)
      ),
      deltaWeightWidth = maxWidth(DeltaWeightHeader +: (rows.map(_.deltaWeight) :+ targetDeltaValue)),
      ownLinesWidth = maxWidth(OwnLinesHeader +: (rows.map(_.ownLines) :+ targetOwnLinesValue)),
      absoluteLinesWidth = maxWidth(
        AbsoluteLinesHeader +: (rows.map(_.absoluteLines) :+ targetAbsoluteLinesValue)
      ),
      deltaLinesWidth = maxWidth(DeltaLinesHeader +: (rows.map(_.deltaLines) :+ targetDeltaLinesValue)),
      ownClassesWidth = maxWidth(OwnClassesHeader +: (rows.map(_.ownClasses) :+ targetOwnClassesValue)),
      usedClassesWidth = maxWidth(UsedClassesHeader +: (rows.map(_.usedClasses) :+ targetUsedClassesValue)),
      absoluteClassesWidth = maxWidth(
        AbsoluteClassesHeader +: (rows.map(_.absoluteClasses) :+ targetAbsoluteClassesValue)
      ),
      noteWidth = maxWidth(NoteHeader +: (notes :+ targetNoteValue)),
      showNotes = (notes :+ targetNoteValue).exists(_.nonEmpty)
    )
  }

  private def appendTarget(
      builder: StringBuilder,
      moduleName: String,
      report: StrictDepsWeightReport,
      layout: DepthLayout
  ): Unit = {
    val ownValue = formatComparison(report.currentModuleSources)
    val totalValue = formatComparison(report.totalSources)
    val deltaValue = formatComparison(report.currentModuleSources)
    val ownLinesValue = formatComparison(report.currentModuleSourceLines)
    val totalLinesValue = formatComparison(report.totalSourceLines)
    val deltaLinesValue = formatComparison(report.currentModuleSourceLines)
    val ownClassesValue = report.currentModuleClassCount.toString
    val usedClassesValue = ""
    val totalClassesValue = report.totalClassCount.toString
    val note = targetNote(report)

    appendTableRow(
      builder = builder,
      layout = layout,
      depthValue = "target",
      moduleValue = display(moduleName),
      relationshipValue = TargetRelationship,
      ownValue = ownValue,
      absoluteValue = totalValue,
      deltaValue = deltaValue,
      ownLinesValue = ownLinesValue,
      absoluteLinesValue = totalLinesValue,
      deltaLinesValue = deltaLinesValue,
      ownClassesValue = ownClassesValue,
      usedClassesValue = usedClassesValue,
      absoluteClassesValue = totalClassesValue,
      noteValue = note
    )
    appendTableRow(
      builder = builder,
      layout = layout,
      depthValue = s"depth ${report.targetDepthIndex}",
      moduleValue = "",
      relationshipValue = "",
      ownValue = "",
      absoluteValue = "",
      deltaValue = "",
      ownLinesValue = "",
      absoluteLinesValue = "",
      deltaLinesValue = "",
      ownClassesValue = "",
      usedClassesValue = "",
      absoluteClassesValue = "",
      noteValue = ""
    )
  }

  private def appendTableRow(
      builder: StringBuilder,
      layout: DepthLayout,
      depthValue: String,
      moduleValue: String,
      relationshipValue: String,
      ownValue: String,
      absoluteValue: String,
      deltaValue: String,
      ownLinesValue: String,
      absoluteLinesValue: String,
      deltaLinesValue: String,
      ownClassesValue: String,
      usedClassesValue: String,
      absoluteClassesValue: String,
      noteValue: String
  ): Unit = {
    val row = new StringBuilder
    row.append(padRight(depthValue, layout.depthWidth))
    row.append("  ")
    row.append(padRight(moduleValue, layout.moduleWidth))
    row.append("  ")
    row.append(padRight(relationshipValue, layout.relationshipWidth))
    row.append("  ")
    row.append(padLeft(ownValue, layout.ownWeightWidth))
    row.append("  ")
    row.append(padLeft(absoluteValue, layout.absoluteWeightWidth))
    row.append("  ")
    row.append(padLeft(deltaValue, layout.deltaWeightWidth))
    row.append("  ")
    row.append(padLeft(ownLinesValue, layout.ownLinesWidth))
    row.append("  ")
    row.append(padLeft(absoluteLinesValue, layout.absoluteLinesWidth))
    row.append("  ")
    row.append(padLeft(deltaLinesValue, layout.deltaLinesWidth))
    row.append("  ")
    row.append(padLeft(ownClassesValue, layout.ownClassesWidth))
    row.append("  ")
    row.append(padLeft(usedClassesValue, layout.usedClassesWidth))
    row.append("  ")
    row.append(padLeft(absoluteClassesValue, layout.absoluteClassesWidth))
    if (layout.showNotes) {
      row.append("  ")
      row.append(padRight(noteValue, layout.noteWidth))
    }
    builder.append(trimRight(row.result()))
    builder.append("\n")
  }

  private def appendSeparator(builder: StringBuilder, width: Int): Unit = {
    builder.append("-" * width)
    builder.append("\n")
  }

  private def appendSummary(
      builder: StringBuilder,
      report: StrictDepsWeightReport
  ): Unit = {
    val rows = summaryRows(report)
    val showNotes = rows.exists(_.note.nonEmpty)
    val labelWidth = maxWidth(MetricHeader +: rows.map(_.label))
    val countWidth = maxWidth(SourceCountHeader +: rows.map(_.count))
    val noteWidth = maxWidth(NoteHeader +: rows.map(_.note))

    builder.append(padRight(MetricHeader, labelWidth))
    builder.append("  ")
    builder.append(padLeft(SourceCountHeader, countWidth))
    if (showNotes) {
      builder.append("  ")
      builder.append(padRight(NoteHeader, noteWidth))
    }
    builder.append("\n")

    builder.append("-" * labelWidth)
    builder.append("  ")
    builder.append("-" * countWidth)
    if (showNotes) {
      builder.append("  ")
      builder.append("-" * noteWidth)
    }
    builder.append("\n")

    rows.foreach { row =>
      builder.append(padRight(row.label, labelWidth))
      builder.append("  ")
      builder.append(padLeft(row.count, countWidth))
      if (showNotes) {
        builder.append("  ")
        builder.append(padRight(row.note, noteWidth))
      }
      builder.append("\n")
    }
    builder.append("\n")
  }

  private def summaryRows(report: StrictDepsWeightReport): Seq[SummaryRow] = {
    Seq(
      comparisonSummaryRow("current module sources", report.currentModuleSources),
      comparisonSummaryRow("dependency sources", report.dependencySources),
      comparisonSummaryRow("total source weight", report.totalSources),
      comparisonSummaryRow("current module source lines", report.currentModuleSourceLines),
      comparisonSummaryRow("dependency source lines", report.dependencySourceLines),
      comparisonSummaryRow("total source lines", report.totalSourceLines),
      SummaryRow("current module classes", report.currentModuleClassCount.toString, ""),
      SummaryRow("dependency classes", report.dependencyClassCount.toString, ""),
      SummaryRow("total classes", report.totalClassCount.toString, "")
    )
  }

  private def comparisonSummaryRow(
      label: String,
      comparison: StrictDepsSourceWeightComparison
  ): SummaryRow = {
    SummaryRow(
      label = label,
      count = formatComparison(comparison),
      note = comparisonNote(comparison)
    )
  }

  private def renderedRow(weight: StrictDepsModuleWeightComparison): RenderedDepthRow = {
    RenderedDepthRow(
      moduleName = display(weight.moduleName),
      relationship = relationship(weight),
      ownWeight = formatComparison(weight.ownSources),
      absoluteWeight = formatComparison(weight.absoluteSources),
      deltaWeight = formatComparison(weight.compileDepthDeltaSources),
      ownLines = formatComparison(weight.ownSourceLines),
      absoluteLines = formatComparison(weight.absoluteSourceLines),
      deltaLines = formatComparison(weight.compileDepthDeltaSourceLines),
      ownClasses = weight.ownClassCount.toString,
      usedClasses = usedClasses(weight),
      absoluteClasses = weight.absoluteClassCount.toString,
      note = rowNote(weight)
    )
  }

  private def usedClasses(weight: StrictDepsModuleWeightComparison): String = {
    s"${weight.usedClassCount} / ${weight.usedClassTotalCount} (${formatPercent(weight.usedClassPercent)})"
  }

  private def appendComparisonNote(
      builder: StringBuilder,
      report: StrictDepsWeightReport
  ): Unit = {
    val comparisons = Seq(
      report.currentModuleSources,
      report.dependencySources,
      report.totalSources,
      report.currentModuleSourceLines,
      report.dependencySourceLines,
      report.totalSourceLines
    ) ++ report.dependencyWeights.flatMap { weight =>
      Seq(
        weight.ownSources,
        weight.absoluteSources,
        weight.compileDepthDeltaSources,
        weight.ownSourceLines,
        weight.absoluteSourceLines,
        weight.compileDepthDeltaSourceLines
      )
    }

    if (!comparisons.forall(_.matches)) {
      builder.append(
        "Note: Mill is planned compiler input; Zinc is compiled-analysis receipt. " +
          "Differences usually mean generated or wrapped sources, stale analysis, or source filtering.\n\n"
      )
    }
  }

  private def relationship(weight: StrictDepsModuleWeightComparison): String = {
    if (weight.declaredDirect) {
      "direct"
    } else {
      "transitive"
    }
  }

  private def moduleCountLabel(count: Int): String = {
    if (count == 1) {
      "1 module"
    } else {
      s"$count modules"
    }
  }

  private def formatComparison(comparison: StrictDepsSourceWeightComparison): String = {
    if (comparison.matches) {
      comparison.millSourceCount.toString
    } else {
      s"${comparison.millSourceCount} Mill / ${comparison.zincSourceCount} Zinc"
    }
  }

  private def comparisonNote(comparison: StrictDepsSourceWeightComparison): String = {
    if (comparison.matches) {
      ""
    } else {
      s"Mill-Zinc ${formatSigned(comparison.millSourceCount - comparison.zincSourceCount)}"
    }
  }

  private def rowNote(weight: StrictDepsModuleWeightComparison): String = {
    Seq(
      "own weight" -> weight.ownSources,
      "absolute weight" -> weight.absoluteSources,
      "delta weight" -> weight.compileDepthDeltaSources,
      "own lines" -> weight.ownSourceLines,
      "absolute lines" -> weight.absoluteSourceLines,
      "delta lines" -> weight.compileDepthDeltaSourceLines
    ).flatMap { case (label, comparison) =>
      Option.when(!comparison.matches) {
        s"$label Mill-Zinc ${formatSigned(comparison.millSourceCount - comparison.zincSourceCount)}"
      }
    }.mkString("; ")
  }

  private def targetNote(report: StrictDepsWeightReport): String = {
    Seq(
      "own weight" -> report.currentModuleSources,
      "total weight" -> report.totalSources,
      "own lines" -> report.currentModuleSourceLines,
      "total lines" -> report.totalSourceLines
    ).flatMap { case (label, comparison) =>
      Option.when(!comparison.matches) {
        s"$label Mill-Zinc ${formatSigned(comparison.millSourceCount - comparison.zincSourceCount)}"
      }
    }.mkString("; ")
  }

  private def formatSigned(value: Int): String = {
    if (value >= 0) {
      s"+$value"
    } else {
      value.toString
    }
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

  private final case class DepthLayout(
      depthWidth: Int,
      moduleWidth: Int,
      relationshipWidth: Int,
      ownWeightWidth: Int,
      absoluteWeightWidth: Int,
      deltaWeightWidth: Int,
      ownLinesWidth: Int,
      absoluteLinesWidth: Int,
      deltaLinesWidth: Int,
      ownClassesWidth: Int,
      usedClassesWidth: Int,
      absoluteClassesWidth: Int,
      noteWidth: Int,
      showNotes: Boolean
  ) {
    def tableWidth: Int = {
      depthWidth +
        2 + moduleWidth +
        2 + relationshipWidth +
        2 + ownWeightWidth +
        2 + absoluteWeightWidth +
        2 + deltaWeightWidth +
        2 + ownLinesWidth +
        2 + absoluteLinesWidth +
        2 + deltaLinesWidth +
        2 + ownClassesWidth +
        2 + usedClassesWidth +
        2 + absoluteClassesWidth +
        (if (showNotes) 2 + noteWidth else 0)
    }
  }

  private final case class SummaryRow(
      label: String,
      count: String,
      note: String
  )

  private final case class RenderedDepthRow(
      moduleName: String,
      relationship: String,
      ownWeight: String,
      absoluteWeight: String,
      deltaWeight: String,
      ownLines: String,
      absoluteLines: String,
      deltaLines: String,
      ownClasses: String,
      usedClasses: String,
      absoluteClasses: String,
      note: String
  )
}
