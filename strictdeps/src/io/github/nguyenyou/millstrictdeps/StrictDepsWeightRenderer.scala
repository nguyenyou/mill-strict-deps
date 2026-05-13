package io.github.nguyenyou.millstrictdeps

object StrictDepsWeightRenderer {
  private val MetricHeader = "metric"
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
  private val ReachableClassesHeader = "reachable classes"
  private val ReachableSourcesHeader = "reachable sources"
  private val AbsoluteClassesHeader = "absolute classes"
  private val SourceCountHeader = "count"
  private val NoteHeader = "note"

  def render(
      moduleName: String,
      report: StrictDepsWeightReport
  ): String = {
    val builder = new StringBuilder
    appendSummary(builder, report)
    appendComparisonNote(builder, report)

    if (report.dependencyWeights.isEmpty) {
      builder.append("No dependency module sources recorded by Mill allSourceFiles or Zinc analysis.\n")
    } else {
      val sortedWeights = report.dependencyWeights.sortBy { weight =>
        (
          -weight.absoluteSources.maxSourceCount,
          if (weight.declaredDirect) 0 else 1,
          weight.moduleName
        )
      }
      val reachabilityByModule = report.reachability.modules.map(module => module.moduleName -> module).toMap
      val rows = sortedWeights.map(weight => renderedRow(weight, reachabilityByModule))
      val showNotes = rows.exists(_.note.nonEmpty)
      val moduleWidth = maxWidth(ModuleHeader +: rows.map(_.moduleName))
      val relationshipWidth = maxWidth(RelationshipHeader +: rows.map(_.relationship))
      val ownWeightWidth = maxWidth(OwnWeightHeader +: rows.map(_.ownWeight))
      val absoluteWeightWidth = maxWidth(AbsoluteWeightHeader +: rows.map(_.absoluteWeight))
      val deltaWeightWidth = maxWidth(DeltaWeightHeader +: rows.map(_.deltaWeight))
      val ownLinesWidth = maxWidth(OwnLinesHeader +: rows.map(_.ownLines))
      val absoluteLinesWidth = maxWidth(AbsoluteLinesHeader +: rows.map(_.absoluteLines))
      val deltaLinesWidth = maxWidth(DeltaLinesHeader +: rows.map(_.deltaLines))
      val ownClassesWidth = maxWidth(OwnClassesHeader +: rows.map(_.ownClasses))
      val usedClassesWidth = maxWidth(UsedClassesHeader +: rows.map(_.usedClasses))
      val reachableClassesWidth = maxWidth(ReachableClassesHeader +: rows.map(_.reachableClasses))
      val reachableSourcesWidth = maxWidth(ReachableSourcesHeader +: rows.map(_.reachableSources))
      val absoluteClassesWidth = maxWidth(AbsoluteClassesHeader +: rows.map(_.absoluteClasses))
      val noteWidth = maxWidth(NoteHeader +: rows.map(_.note))

      builder.append(padRight(ModuleHeader, moduleWidth))
      builder.append("  ")
      builder.append(padRight(RelationshipHeader, relationshipWidth))
      builder.append("  ")
      builder.append(padLeft(OwnWeightHeader, ownWeightWidth))
      builder.append("  ")
      builder.append(padLeft(AbsoluteWeightHeader, absoluteWeightWidth))
      builder.append("  ")
      builder.append(padLeft(DeltaWeightHeader, deltaWeightWidth))
      builder.append("  ")
      builder.append(padLeft(OwnLinesHeader, ownLinesWidth))
      builder.append("  ")
      builder.append(padLeft(AbsoluteLinesHeader, absoluteLinesWidth))
      builder.append("  ")
      builder.append(padLeft(DeltaLinesHeader, deltaLinesWidth))
      builder.append("  ")
      builder.append(padLeft(OwnClassesHeader, ownClassesWidth))
      builder.append("  ")
      builder.append(padLeft(UsedClassesHeader, usedClassesWidth))
      builder.append("  ")
      builder.append(padLeft(ReachableClassesHeader, reachableClassesWidth))
      builder.append("  ")
      builder.append(padLeft(ReachableSourcesHeader, reachableSourcesWidth))
      builder.append("  ")
      builder.append(padLeft(AbsoluteClassesHeader, absoluteClassesWidth))
      if (showNotes) {
        builder.append("  ")
        builder.append(padRight(NoteHeader, noteWidth))
      }
      builder.append("\n")

      builder.append("-" * moduleWidth)
      builder.append("  ")
      builder.append("-" * relationshipWidth)
      builder.append("  ")
      builder.append("-" * ownWeightWidth)
      builder.append("  ")
      builder.append("-" * absoluteWeightWidth)
      builder.append("  ")
      builder.append("-" * deltaWeightWidth)
      builder.append("  ")
      builder.append("-" * ownLinesWidth)
      builder.append("  ")
      builder.append("-" * absoluteLinesWidth)
      builder.append("  ")
      builder.append("-" * deltaLinesWidth)
      builder.append("  ")
      builder.append("-" * ownClassesWidth)
      builder.append("  ")
      builder.append("-" * usedClassesWidth)
      builder.append("  ")
      builder.append("-" * reachableClassesWidth)
      builder.append("  ")
      builder.append("-" * reachableSourcesWidth)
      builder.append("  ")
      builder.append("-" * absoluteClassesWidth)
      if (showNotes) {
        builder.append("  ")
        builder.append("-" * noteWidth)
      }
      builder.append("\n")

      rows.foreach { row =>
        builder.append(padRight(row.moduleName, moduleWidth))
        builder.append("  ")
        builder.append(padRight(row.relationship, relationshipWidth))
        builder.append("  ")
        builder.append(padLeft(row.ownWeight, ownWeightWidth))
        builder.append("  ")
        builder.append(padLeft(row.absoluteWeight, absoluteWeightWidth))
        builder.append("  ")
        builder.append(padLeft(row.deltaWeight, deltaWeightWidth))
        builder.append("  ")
        builder.append(padLeft(row.ownLines, ownLinesWidth))
        builder.append("  ")
        builder.append(padLeft(row.absoluteLines, absoluteLinesWidth))
        builder.append("  ")
        builder.append(padLeft(row.deltaLines, deltaLinesWidth))
        builder.append("  ")
        builder.append(padLeft(row.ownClasses, ownClassesWidth))
        builder.append("  ")
        builder.append(padLeft(row.usedClasses, usedClassesWidth))
        builder.append("  ")
        builder.append(padLeft(row.reachableClasses, reachableClassesWidth))
        builder.append("  ")
        builder.append(padLeft(row.reachableSources, reachableSourcesWidth))
        builder.append("  ")
        builder.append(padLeft(row.absoluteClasses, absoluteClassesWidth))
        if (showNotes) {
          builder.append("  ")
          builder.append(padRight(row.note, noteWidth))
        }
        builder.append("\n")
      }
    }

    builder.result()
  }

  private def relationship(weight: StrictDepsModuleWeightComparison): String = {
    if (weight.declaredDirect) {
      "direct"
    } else {
      "transitive"
    }
  }

  private def renderedRow(
      weight: StrictDepsModuleWeightComparison,
      reachabilityByModule: Map[String, StrictDepsModuleReachability]
  ): RenderedWeightRow = {
    RenderedWeightRow(
      moduleName = display(weight.moduleName),
      relationship = relationship(weight),
      ownWeight = formatComparison(weight.ownSources),
      absoluteWeight = formatComparison(weight.absoluteSources),
      deltaWeight = formatComparison(weight.deltaSources),
      ownLines = formatComparison(weight.ownSourceLines),
      absoluteLines = formatComparison(weight.absoluteSourceLines),
      deltaLines = formatComparison(weight.deltaSourceLines),
      ownClasses = weight.ownClassCount.toString,
      usedClasses = usedClasses(weight),
      reachableClasses = reachableClasses(weight, reachabilityByModule),
      reachableSources = reachableSources(weight, reachabilityByModule),
      absoluteClasses = weight.absoluteClassCount.toString,
      note = rowNote(weight)
    )
  }

  private def usedClasses(weight: StrictDepsModuleWeightComparison): String = {
    if (weight.usedClassCount == 0) {
      "zero"
    } else {
      s"${weight.usedClassCount} / ${weight.usedClassTotalCount} (${formatPercent(weight.usedClassPercent)})"
    }
  }

  private def reachableClasses(
      weight: StrictDepsModuleWeightComparison,
      reachabilityByModule: Map[String, StrictDepsModuleReachability]
  ): String = {
    reachabilityByModule.get(weight.moduleName) match {
      case Some(reachability) if reachability.reachableClassCount == 0 =>
        "zero"
      case Some(reachability) =>
        formatReachability(
          reached = reachability.reachableClassCount,
          total = reachability.providedClassCount,
          percent = reachability.reachableClassPercent
        )
      case None =>
        ""
    }
  }

  private def reachableSources(
      weight: StrictDepsModuleWeightComparison,
      reachabilityByModule: Map[String, StrictDepsModuleReachability]
  ): String = {
    reachabilityByModule.get(weight.moduleName) match {
      case Some(reachability) if reachability.reachableSourceCount == 0 =>
        "zero"
      case Some(reachability) =>
        formatReachability(
          reached = reachability.reachableSourceCount,
          total = reachability.providedSourceCount,
          percent = reachability.reachableSourcePercent
        )
      case None =>
        ""
    }
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
        SummaryRow("direct used dependency classes", reachability.directUsedClassCount.toString, ""),
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
        SummaryRow("direct used dependency sources", reachability.directUsedSourceCount.toString, ""),
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
        weight.deltaSources,
        weight.ownSourceLines,
        weight.absoluteSourceLines,
        weight.deltaSourceLines
      )
    }

    if (!comparisons.forall(_.matches)) {
      builder.append(
        "Note: Mill is planned compiler input; Zinc is compiled-analysis receipt. " +
          "Differences usually mean generated or wrapped sources, stale analysis, or source filtering.\n\n"
      )
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
      "delta weight" -> weight.deltaSources,
      "own lines" -> weight.ownSourceLines,
      "absolute lines" -> weight.absoluteSourceLines,
      "delta lines" -> weight.deltaSourceLines
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

  private def display(value: String): String = {
    value
      .replace("\r", " ")
      .replace("\n", " ")
  }

  private final case class SummaryRow(
      label: String,
      count: String,
      note: String
  )

  private final case class RenderedWeightRow(
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
      reachableClasses: String,
      reachableSources: String,
      absoluteClasses: String,
      note: String
  )
}
