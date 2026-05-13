package io.github.nguyenyou.millstrictdeps

object StrictDepsCompileDepthRenderer {
  private val MetricHeader = "metric"
  private val DepthHeader = "depth"
  private val ModuleHeader = "module"
  private val RelationshipHeader = "relationship"
  private val OwnWeightHeader = "own weight"
  private val AbsoluteWeightHeader = "absolute weight"
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
      noteValue = Option.when(layout.showNotes)(NoteHeader).getOrElse("")
    )
  }

  private def appendDepth(
      builder: StringBuilder,
      depth: StrictDepsCompileDepth,
      layout: DepthLayout
  ): Unit = {
    val ownWeightValues = depth.modules.map(weight => formatComparison(weight.ownSources))
    val absoluteWeightValues = depth.modules.map(weight => formatComparison(weight.absoluteSources))
    val notes = depth.modules.map(rowNote)

    depth.modules.zip(ownWeightValues).zip(absoluteWeightValues).zip(notes).zipWithIndex.foreach {
      case ((((weight, ownValue), absoluteValue), note), index) =>
        appendTableRow(
          builder = builder,
          layout = layout,
          depthValue = depthCell(depth, index),
          moduleValue = display(weight.moduleName),
          relationshipValue = relationship(weight),
          ownValue = ownValue,
          absoluteValue = absoluteValue,
          noteValue = note
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
    val targetNoteValue = targetNote(report)
    val ownWeightValues = weights.map(weight => formatComparison(weight.ownSources))
    val absoluteWeightValues = weights.map(weight => formatComparison(weight.absoluteSources))
    val notes = weights.map(rowNote)
    val depthValues = depths.flatMap { depth =>
      Seq(s"depth ${depth.index}", moduleCountLabel(depth.modules.size))
    } ++ Seq("target", s"depth ${report.targetDepthIndex}")

    DepthLayout(
      depthWidth = maxWidth(DepthHeader +: depthValues),
      moduleWidth = maxWidth(ModuleHeader +: (weights.map(weight => display(weight.moduleName)) :+ display(moduleName))),
      relationshipWidth = maxWidth(RelationshipHeader +: (weights.map(relationship) :+ TargetRelationship)),
      ownWeightWidth = maxWidth(OwnWeightHeader +: (ownWeightValues :+ targetOwnValue)),
      absoluteWeightWidth = maxWidth(AbsoluteWeightHeader +: (absoluteWeightValues :+ targetAbsoluteValue)),
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
    val note = targetNote(report)

    appendTableRow(
      builder = builder,
      layout = layout,
      depthValue = "target",
      moduleValue = display(moduleName),
      relationshipValue = TargetRelationship,
      ownValue = ownValue,
      absoluteValue = totalValue,
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
    val rows = Seq(
      "current module sources" -> report.currentModuleSources,
      "dependency sources" -> report.dependencySources,
      "total source weight" -> report.totalSources
    )
    val sourceCounts = rows.map { case (_, comparison) => formatComparison(comparison) }
    val notes = rows.map { case (_, comparison) => comparisonNote(comparison) }
    val showNotes = notes.exists(_.nonEmpty)
    val labelWidth = maxWidth(MetricHeader +: rows.map { case (label, _) => label })
    val countWidth = maxWidth(SourceCountHeader +: sourceCounts)
    val noteWidth = maxWidth(NoteHeader +: notes)

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

    rows.zip(sourceCounts).zip(notes).foreach { case (((label, _), count), note) =>
      builder.append(padRight(label, labelWidth))
      builder.append("  ")
      builder.append(padLeft(count, countWidth))
      if (showNotes) {
        builder.append("  ")
        builder.append(padRight(note, noteWidth))
      }
      builder.append("\n")
    }
    builder.append("\n")
  }

  private def appendComparisonNote(
      builder: StringBuilder,
      report: StrictDepsWeightReport
  ): Unit = {
    val comparisons = Seq(
      report.currentModuleSources,
      report.dependencySources,
      report.totalSources
    ) ++ report.dependencyWeights.flatMap { weight =>
      Seq(weight.ownSources, weight.absoluteSources)
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
      "own" -> weight.ownSources,
      "absolute" -> weight.absoluteSources
    ).flatMap { case (label, comparison) =>
      Option.when(!comparison.matches) {
        s"$label Mill-Zinc ${formatSigned(comparison.millSourceCount - comparison.zincSourceCount)}"
      }
    }.mkString("; ")
  }

  private def targetNote(report: StrictDepsWeightReport): String = {
    Seq(
      "own" -> report.currentModuleSources,
      "total" -> report.totalSources
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
      noteWidth: Int,
      showNotes: Boolean
  ) {
    def tableWidth: Int = {
      depthWidth +
        2 + moduleWidth +
        2 + relationshipWidth +
        2 + ownWeightWidth +
        2 + absoluteWeightWidth +
        (if (showNotes) 2 + noteWidth else 0)
    }
  }
}
