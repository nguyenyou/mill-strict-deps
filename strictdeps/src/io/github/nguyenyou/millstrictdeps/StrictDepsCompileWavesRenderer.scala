package io.github.nguyenyou.millstrictdeps

object StrictDepsCompileWavesRenderer {
  private val MetricHeader = "metric"
  private val ModuleHeader = "module"
  private val RelationshipHeader = "relationship"
  private val OwnWeightHeader = "own weight"
  private val AbsoluteWeightHeader = "absolute weight"
  private val TotalWeightHeader = "total weight"
  private val SourceCountHeader = "count"
  private val NoteHeader = "note"

  def render(
      moduleName: String,
      report: StrictDepsWeightReport
  ): String = {
    val builder = new StringBuilder
    appendSummary(builder, report)
    appendComparisonNote(builder, report)

    if (report.compileWaves.isEmpty) {
      builder.append("No dependency module sources recorded by Mill allSourceFiles or Zinc analysis.\n\n")
    } else {
      val layout = waveLayout(report.compileWaves)
      report.compileWaves.zipWithIndex.foreach { case (wave, index) =>
        appendWave(builder, wave, layout)
        if (index < report.compileWaves.size - 1) {
          appendSeparator(builder, layout.tableWidth)
        }
      }
      appendSeparator(builder, layout.tableWidth)
    }

    appendTarget(builder, moduleName, report)
    builder.result()
  }

  private def appendWave(
      builder: StringBuilder,
      wave: StrictDepsCompileWave,
      layout: WaveLayout
  ): Unit = {
    builder.append(s"compile wave ${wave.index}  ${moduleCountLabel(wave.modules.size)}\n")

    val ownWeightValues = wave.modules.map(weight => formatComparison(weight.ownSources))
    val absoluteWeightValues = wave.modules.map(weight => formatComparison(weight.absoluteSources))
    val notes = wave.modules.map(rowNote)

    builder.append(padRight(ModuleHeader, layout.moduleWidth))
    builder.append("  ")
    builder.append(padRight(RelationshipHeader, layout.relationshipWidth))
    builder.append("  ")
    builder.append(padLeft(OwnWeightHeader, layout.ownWeightWidth))
    builder.append("  ")
    builder.append(padLeft(AbsoluteWeightHeader, layout.absoluteWeightWidth))
    if (layout.showNotes) {
      builder.append("  ")
      builder.append(padRight(NoteHeader, layout.noteWidth))
    }
    builder.append("\n")

    wave.modules.zip(ownWeightValues).zip(absoluteWeightValues).zip(notes).foreach {
      case (((weight, ownValue), absoluteValue), note) =>
        builder.append(padRight(display(weight.moduleName), layout.moduleWidth))
        builder.append("  ")
        builder.append(padRight(relationship(weight), layout.relationshipWidth))
        builder.append("  ")
        builder.append(padLeft(ownValue, layout.ownWeightWidth))
        builder.append("  ")
        builder.append(padLeft(absoluteValue, layout.absoluteWeightWidth))
        if (layout.showNotes) {
          builder.append("  ")
          builder.append(padRight(note, layout.noteWidth))
        }
        builder.append("\n")
    }
  }

  private def waveLayout(waves: Seq[StrictDepsCompileWave]): WaveLayout = {
    val weights = waves.flatMap(_.modules)
    val ownWeightValues = weights.map(weight => formatComparison(weight.ownSources))
    val absoluteWeightValues = weights.map(weight => formatComparison(weight.absoluteSources))
    val notes = weights.map(rowNote)

    WaveLayout(
      moduleWidth = maxWidth(ModuleHeader +: weights.map(weight => display(weight.moduleName))),
      relationshipWidth = maxWidth(RelationshipHeader +: weights.map(relationship)),
      ownWeightWidth = maxWidth(OwnWeightHeader +: ownWeightValues),
      absoluteWeightWidth = maxWidth(AbsoluteWeightHeader +: absoluteWeightValues),
      noteWidth = maxWidth(NoteHeader +: notes),
      showNotes = notes.exists(_.nonEmpty)
    )
  }

  private def appendTarget(
      builder: StringBuilder,
      moduleName: String,
      report: StrictDepsWeightReport
  ): Unit = {
    val ownValue = formatComparison(report.currentModuleSources)
    val totalValue = formatComparison(report.totalSources)
    val note = targetNote(report)
    val showNote = note.nonEmpty
    val moduleWidth = maxWidth(Seq(ModuleHeader, display(moduleName)))
    val ownWeightWidth = maxWidth(Seq(OwnWeightHeader, ownValue))
    val totalWeightWidth = maxWidth(Seq(TotalWeightHeader, totalValue))
    val noteWidth = maxWidth(Seq(NoteHeader, note))

    builder.append(s"target wave ${report.targetWaveIndex}\n")
    builder.append(padRight(ModuleHeader, moduleWidth))
    builder.append("  ")
    builder.append(padLeft(OwnWeightHeader, ownWeightWidth))
    builder.append("  ")
    builder.append(padLeft(TotalWeightHeader, totalWeightWidth))
    if (showNote) {
      builder.append("  ")
      builder.append(padRight(NoteHeader, noteWidth))
    }
    builder.append("\n")

    builder.append(padRight(display(moduleName), moduleWidth))
    builder.append("  ")
    builder.append(padLeft(ownValue, ownWeightWidth))
    builder.append("  ")
    builder.append(padLeft(totalValue, totalWeightWidth))
    if (showNote) {
      builder.append("  ")
      builder.append(padRight(note, noteWidth))
    }
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

  private def display(value: String): String = {
    value
      .replace("\r", " ")
      .replace("\n", " ")
  }

  private final case class WaveLayout(
      moduleWidth: Int,
      relationshipWidth: Int,
      ownWeightWidth: Int,
      absoluteWeightWidth: Int,
      noteWidth: Int,
      showNotes: Boolean
  ) {
    def tableWidth: Int = {
      moduleWidth +
        2 + relationshipWidth +
        2 + ownWeightWidth +
        2 + absoluteWeightWidth +
        (if (showNotes) 2 + noteWidth else 0)
    }
  }
}
