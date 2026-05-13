package io.github.nguyenyou.millstrictdeps

object StrictDepsWeightRenderer {
  private val MetricHeader = "metric"
  private val ModuleHeader = "module"
  private val RelationshipHeader = "relationship"
  private val OwnWeightHeader = "own source weight"
  private val AbsoluteWeightHeader = "absolute source weight"
  private val SourceCountHeader = "source count"
  private val NoteHeader = "note"

  def render(
      moduleName: String,
      report: StrictDepsWeightReport
  ): String = {
    val builder = new StringBuilder
    builder.append(s"Dependency Source Weights: ${display(moduleName)}\n\n")
    appendMechanism(builder)
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
      val ownWeightValues = sortedWeights.map(weight => formatComparison(weight.ownSources))
      val absoluteWeightValues = sortedWeights.map(weight => formatComparison(weight.absoluteSources))
      val notes = sortedWeights.map(rowNote)
      val showNotes = notes.exists(_.nonEmpty)
      val moduleWidth = maxWidth(ModuleHeader +: sortedWeights.map(weight => display(weight.moduleName)))
      val relationshipWidth =
        maxWidth(RelationshipHeader +: sortedWeights.map(relationship))
      val ownWeightWidth = maxWidth(OwnWeightHeader +: ownWeightValues)
      val absoluteWeightWidth = maxWidth(AbsoluteWeightHeader +: absoluteWeightValues)
      val noteWidth = maxWidth(NoteHeader +: notes)

      builder.append(padRight(ModuleHeader, moduleWidth))
      builder.append("  ")
      builder.append(padRight(RelationshipHeader, relationshipWidth))
      builder.append("  ")
      builder.append(padLeft(OwnWeightHeader, ownWeightWidth))
      builder.append("  ")
      builder.append(padLeft(AbsoluteWeightHeader, absoluteWeightWidth))
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
      if (showNotes) {
        builder.append("  ")
        builder.append("-" * noteWidth)
      }
      builder.append("\n")

      sortedWeights.zip(ownWeightValues).zip(absoluteWeightValues).zip(notes).foreach {
        case (((weight, ownValue), absoluteValue), note) =>
          builder.append(padRight(display(weight.moduleName), moduleWidth))
          builder.append("  ")
          builder.append(padRight(relationship(weight), relationshipWidth))
          builder.append("  ")
          builder.append(padLeft(ownValue, ownWeightWidth))
          builder.append("  ")
          builder.append(padLeft(absoluteValue, absoluteWeightWidth))
          if (showNotes) {
            builder.append("  ")
            builder.append(padRight(note, noteWidth))
          }
          builder.append("\n")
      }
    }

    builder.result()
  }

  private def appendMechanism(builder: StringBuilder): Unit = {
    builder.append("How calculated\n")
    builder.append("- Mill allSourceFiles gives the source files planned for compiler input.\n")
    builder.append("- Zinc allSources gives the source files recorded in compile analysis.\n")
    builder.append("- If Mill and Zinc agree, one number is shown.\n")
    builder.append("- If they differ, both numbers are shown as Mill / Zinc with a note.\n")
    builder.append("- total source weight = distinct current module sources plus dependency sources.\n")
    builder.append("- own source weight = source files from that dependency module only.\n")
    builder.append("- absolute source weight = that dependency module plus modules reachable from it; rows can overlap.\n\n")
  }

  private def relationship(weight: StrictDepsModuleWeightComparison): String = {
    if (weight.declaredDirect) {
      "direct"
    } else {
      "transitive"
    }
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

    if (comparisons.forall(_.matches)) {
      builder.append("Mill and Zinc source counts match.\n\n")
    } else {
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
      "own" -> weight.ownSources,
      "absolute" -> weight.absoluteSources
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
}
