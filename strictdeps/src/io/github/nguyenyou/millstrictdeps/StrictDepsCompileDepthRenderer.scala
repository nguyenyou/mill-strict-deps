package io.github.nguyenyou.millstrictdeps

object StrictDepsCompileDepthRenderer {
  private val MetricHeader = "metric"
  private val DepthHeader = "depth"
  private val ModuleHeader = "module"
  private val RelationshipHeader = "relationship"
  private val OwnSourcesHeader = "own sources"
  private val AbsoluteSourcesHeader = "absolute sources"
  private val DeltaSourcesHeader = "delta sources"
  private val OwnLinesHeader = "own lines"
  private val OwnClassesHeader = "own classes"
  private val UsedClassesHeader = "directly referenced classes"
  private val ReachableClassesHeader = "reachable classes"
  private val ReachableSourcesHeader = "reachable sources"
  private val AbsoluteClassesHeader = "absolute classes"
  private val SourceCountHeader = "count"
  private val NoteHeader = "note"
  private val TargetRelationship = "target"
  private val BarWidth = 10
  private val BarFilled = "█"
  private val BarEmpty = "░"

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
      relationshipValue = fansi.Str(RelationshipHeader),
      ownValue = OwnSourcesHeader,
      absoluteValue = AbsoluteSourcesHeader,
      deltaValue = DeltaSourcesHeader,
      ownLinesValue = OwnLinesHeader,
      ownClassesValue = OwnClassesHeader,
      usedClassesValue = fansi.Str(UsedClassesHeader),
      reachableClassesValue = fansi.Str(ReachableClassesHeader),
      reachableSourcesValue = fansi.Str(ReachableSourcesHeader),
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
        ownClassesValue = row.ownClasses,
        usedClassesValue = row.usedClasses,
        reachableClassesValue = row.reachableClasses,
        reachableSourcesValue = row.reachableSources,
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
        relationshipValue = fansi.Str(""),
        ownValue = "",
        absoluteValue = "",
        deltaValue = "",
        ownLinesValue = "",
        ownClassesValue = "",
        usedClassesValue = fansi.Str(""),
        reachableClassesValue = fansi.Str(""),
        reachableSourcesValue = fansi.Str(""),
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
    val targetOwnClassesValue = report.currentModuleClassCount.toString
    val targetUsedClassesValue = fansi.Str("")
    val targetReachableClassesValue = fansi.Str("")
    val targetReachableSourcesValue = fansi.Str("")
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
      relationshipWidth = fansiMaxWidth(
        RelationshipHeader,
        weights.map(relationship) :+ fansi.Str(TargetRelationship)
      ),
      ownWeightWidth = maxWidth(OwnSourcesHeader +: (rows.map(_.ownWeight) :+ targetOwnValue)),
      absoluteWeightWidth = maxWidth(
        AbsoluteSourcesHeader +: (rows.map(_.absoluteWeight) :+ targetAbsoluteValue)
      ),
      deltaWeightWidth = maxWidth(DeltaSourcesHeader +: (rows.map(_.deltaWeight) :+ targetDeltaValue)),
      ownLinesWidth = maxWidth(OwnLinesHeader +: (rows.map(_.ownLines) :+ targetOwnLinesValue)),
      ownClassesWidth = maxWidth(OwnClassesHeader +: (rows.map(_.ownClasses) :+ targetOwnClassesValue)),
      usedClassesWidth = fansiMaxWidth(
        UsedClassesHeader,
        rows.map(_.usedClasses) :+ targetUsedClassesValue
      ),
      reachableClassesWidth = fansiMaxWidth(
        ReachableClassesHeader,
        rows.map(_.reachableClasses) :+ targetReachableClassesValue
      ),
      reachableSourcesWidth = fansiMaxWidth(
        ReachableSourcesHeader,
        rows.map(_.reachableSources) :+ targetReachableSourcesValue
      ),
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
    val ownClassesValue = report.currentModuleClassCount.toString
    val usedClassesValue = fansi.Str("")
    val reachableClassesValue = fansi.Str("")
    val reachableSourcesValue = fansi.Str("")
    val totalClassesValue = report.totalClassCount.toString
    val note = targetNote(report)

    appendTableRow(
      builder = builder,
      layout = layout,
      depthValue = "target",
      moduleValue = display(moduleName),
      relationshipValue = fansi.Str(TargetRelationship),
      ownValue = ownValue,
      absoluteValue = totalValue,
      deltaValue = deltaValue,
      ownLinesValue = ownLinesValue,
      ownClassesValue = ownClassesValue,
      usedClassesValue = usedClassesValue,
      reachableClassesValue = reachableClassesValue,
      reachableSourcesValue = reachableSourcesValue,
      absoluteClassesValue = totalClassesValue,
      noteValue = note
    )
    appendTableRow(
      builder = builder,
      layout = layout,
      depthValue = s"depth ${report.targetDepthIndex}",
      moduleValue = "",
      relationshipValue = fansi.Str(""),
      ownValue = "",
      absoluteValue = "",
      deltaValue = "",
      ownLinesValue = "",
      ownClassesValue = "",
      usedClassesValue = fansi.Str(""),
      reachableClassesValue = fansi.Str(""),
      reachableSourcesValue = fansi.Str(""),
      absoluteClassesValue = "",
      noteValue = ""
    )
  }

  private def appendTableRow(
      builder: StringBuilder,
      layout: DepthLayout,
      depthValue: String,
      moduleValue: String,
      relationshipValue: fansi.Str,
      ownValue: String,
      absoluteValue: String,
      deltaValue: String,
      ownLinesValue: String,
      ownClassesValue: String,
      usedClassesValue: fansi.Str,
      reachableClassesValue: fansi.Str,
      reachableSourcesValue: fansi.Str,
      absoluteClassesValue: String,
      noteValue: String
  ): Unit = {
    val row = new StringBuilder
    row.append(padRight(depthValue, layout.depthWidth))
    row.append("  ")
    row.append(padRight(moduleValue, layout.moduleWidth))
    row.append("  ")
    row.append(padRightFansi(relationshipValue, layout.relationshipWidth))
    row.append("  ")
    row.append(padLeft(ownValue, layout.ownWeightWidth))
    row.append("  ")
    row.append(padLeft(absoluteValue, layout.absoluteWeightWidth))
    row.append("  ")
    row.append(padLeft(deltaValue, layout.deltaWeightWidth))
    row.append("  ")
    row.append(padLeft(ownLinesValue, layout.ownLinesWidth))
    row.append("  ")
    row.append(padLeft(ownClassesValue, layout.ownClassesWidth))
    row.append("  ")
    row.append(padLeftFansi(usedClassesValue, layout.usedClassesWidth))
    row.append("  ")
    row.append(padLeftFansi(reachableClassesValue, layout.reachableClassesWidth))
    row.append("  ")
    row.append(padLeftFansi(reachableSourcesValue, layout.reachableSourcesWidth))
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
    ) ++ reachabilitySummaryRows(report.reachability)
  }

  private def reachabilitySummaryRows(reachability: StrictDepsReachabilityReport): Seq[SummaryRow] = {
    if (reachability.providedClassCount == 0 && reachability.providedSourceCount == 0) {
      Seq.empty
    } else {
      Seq(
        SummaryRow("directly referenced dependency classes", reachability.directUsedClassCount.toString, ""),
        SummaryRow(
          "reachable dependency classes",
          formatReachability(
            reached = reachability.reachableClassCount,
            total = reachability.providedClassCount,
            percent = reachability.reachableClassPercent
          ),
          ""
        ),
        SummaryRow("unused dependency classes", reachability.unusedClassCount.toString, ""),
        SummaryRow("directly referenced dependency sources", reachability.directUsedSourceCount.toString, ""),
        SummaryRow(
          "reachable dependency sources",
          formatReachability(
            reached = reachability.reachableSourceCount,
            total = reachability.providedSourceCount,
            percent = reachability.reachableSourcePercent
          ),
          ""
        ),
        SummaryRow("unused dependency sources", reachability.unusedSourceCount.toString, "")
      )
    }
  }

  private def formatReachability(
      reached: Int,
      total: Int,
      percent: Double
  ): String = {
    s"$reached / $total (${formatPercent(percent)})"
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
      ownClasses = weight.ownClassCount.toString,
      usedClasses = usedClasses(weight),
      reachableClasses = reachableClasses(weight),
      reachableSources = reachableSources(weight),
      absoluteClasses = weight.absoluteClassCount.toString,
      note = rowNote(weight)
    )
  }

  private def usedClasses(weight: StrictDepsModuleWeightComparison): fansi.Str = {
    countAndBar(weight.usedClassCount, weight.usedClassTotalCount, weight.usedClassPercent)
  }

  private def reachableClasses(weight: StrictDepsModuleWeightComparison): fansi.Str = {
    countAndBar(weight.reachableClassCount, weight.reachableClassTotalCount, weight.reachableClassPercent)
  }

  private def reachableSources(weight: StrictDepsModuleWeightComparison): fansi.Str = {
    countAndBar(weight.reachableSourceCount, weight.reachableSourceTotalCount, weight.reachableSourcePercent)
  }

  private def countAndBar(count: Int, total: Int, percent: Double): fansi.Str = {
    val countText = s"$count / $total "
    val prefix = if (count == 0) fansi.Color.Red(countText) else fansi.Str(countText)
    prefix ++ progressBar(percent)
  }

  private def progressBar(percent: Double): fansi.Str = {
    val p = math.max(0.0, math.min(1.0, percent / 100.0))
    val filled = math.round(BarWidth * p).toInt
    val bar = (BarFilled * filled) + (BarEmpty * (BarWidth - filled))
    percentAttrs(percent)(bar)
  }

  private def percentAttrs(percent: Double): fansi.Attrs = {
    val p = math.max(0.0, math.min(1.0, percent / 100.0))
    val r = math.min(1.0, 2.0 * (1.0 - p))
    val g = math.min(1.0, 2.0 * p)
    fansi.Color.True((r * 255).toInt, (g * 255).toInt, 0)
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
        weight.ownSourceLines
      )
    }

    if (!comparisons.forall(_.matches)) {
      builder.append(
        "Note: Mill is planned compiler input; Zinc is compiled-analysis receipt. " +
          "Differences usually mean generated or wrapped sources, stale analysis, or source filtering.\n\n"
      )
    }
  }

  private def relationship(weight: StrictDepsModuleWeightComparison): fansi.Str = {
    if (weight.declaredDirect) {
      fansi.Color.Green("█") ++ fansi.Str(" direct")
    } else {
      fansi.Color.Blue("█") ++ fansi.Str(" transitive")
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
      "own sources" -> weight.ownSources,
      "absolute sources" -> weight.absoluteSources,
      "delta sources" -> weight.compileDepthDeltaSources,
      "own lines" -> weight.ownSourceLines
    ).flatMap { case (label, comparison) =>
      Option.when(!comparison.matches) {
        s"$label Mill-Zinc ${formatSigned(comparison.millSourceCount - comparison.zincSourceCount)}"
      }
    }.mkString("; ")
  }

  private def targetNote(report: StrictDepsWeightReport): String = {
    Seq(
      "own sources" -> report.currentModuleSources,
      "total sources" -> report.totalSources,
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

  private def fansiMaxWidth(header: String, values: Seq[fansi.Str]): Int = {
    (header.length +: values.map(_.length)).max
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

  private def padLeftFansi(value: fansi.Str, width: Int): String = {
    (" " * math.max(0, width - value.length)) + value.render
  }

  private def padRightFansi(value: fansi.Str, width: Int): String = {
    value.render + (" " * math.max(0, width - value.length))
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
      ownClassesWidth: Int,
      usedClassesWidth: Int,
      reachableClassesWidth: Int,
      reachableSourcesWidth: Int,
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
        2 + ownClassesWidth +
        2 + usedClassesWidth +
        2 + reachableClassesWidth +
        2 + reachableSourcesWidth +
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
      relationship: fansi.Str,
      ownWeight: String,
      absoluteWeight: String,
      deltaWeight: String,
      ownLines: String,
      ownClasses: String,
      usedClasses: fansi.Str,
      reachableClasses: fansi.Str,
      reachableSources: fansi.Str,
      absoluteClasses: String,
      note: String
  )
}
