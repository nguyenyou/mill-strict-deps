package io.github.nguyenyou.millstrictdeps

object StrictDepsDownstreamUsageRenderer {
  private val MetricHeader = "metric"
  private val ValueHeader = "value"
  private val CountHeader = ""
  private val ClientHeader = "client"
  private val RelationshipHeader = "relationship"
  private val IntroducedByHeader = "introduced by"
  private val UsedClassesHeader = "directly referenced classes"
  private val ReachableClassesHeader = "reachable classes"
  private val ReachableSourcesHeader = "reachable sources"
  private val BarWidth = 10
  private val BarFilled = "█"
  private val BarEmpty = "░"

  def render(
      report: StrictDepsDownstreamUsageReport,
      limit: Int
  ): String = {
    val builder = new StringBuilder
    appendSummary(builder, report)

    if (report.downstreamModules.isEmpty) {
      builder.append("No downstream modules containing target were collected.\n")
    } else {
      val rows =
        if (limit <= 0) {
          report.downstreamModules
        } else {
          report.downstreamModules.take(limit)
        }
      appendModuleTable(builder, rows)

      if (limit > 0 && report.downstreamModules.size > limit) {
        builder.append(s"... ${report.downstreamModules.size - limit} more modules\n")
      }
    }

    builder.result()
  }

  private def appendSummary(
      builder: StringBuilder,
      report: StrictDepsDownstreamUsageReport
  ): Unit = {
    val rows = Seq(
      "target module" -> report.targetModuleName,
      "root modules" -> report.rootModuleCount.toString,
      "downstream modules" -> report.downstreamModuleCount.toString,
      "direct downstream modules" -> report.directDownstreamModuleCount.toString
    )
    val labelWidth = maxWidth(MetricHeader +: rows.map { case (label, _) => label })
    val valueWidth = maxWidth(ValueHeader +: rows.map { case (_, value) => value })

    builder.append(padRight(MetricHeader, labelWidth))
    builder.append("  ")
    builder.append(padLeft(ValueHeader, valueWidth))
    builder.append("\n")
    builder.append("-" * labelWidth)
    builder.append("  ")
    builder.append("-" * valueWidth)
    builder.append("\n")

    rows.foreach { case (label, value) =>
      builder.append(padRight(label, labelWidth))
      builder.append("  ")
      builder.append(padLeft(value, valueWidth))
      builder.append("\n")
    }
    builder.append("\n")
  }

  private def appendModuleTable(
      builder: StringBuilder,
      modules: Seq[StrictDepsDownstreamUsageModule]
  ): Unit = {
    val rows = modules.zipWithIndex.map { case (module, index) =>
      renderedRow(module, index)
    }
    val layout = TableLayout(
      countWidth = maxWidth(CountHeader +: rows.map(_.count)),
      clientWidth = maxWidth(ClientHeader +: rows.map(_.client)),
      relationshipWidth = fansiMaxWidth(RelationshipHeader, rows.map(_.relationship)),
      introducedByWidth = maxWidth(IntroducedByHeader +: rows.map(_.introducedBy)),
      usedClassesWidth = fansiMaxWidth(UsedClassesHeader, rows.map(_.usedClasses)),
      reachableClassesWidth = fansiMaxWidth(ReachableClassesHeader, rows.map(_.reachableClasses)),
      reachableSourcesWidth = fansiMaxWidth(ReachableSourcesHeader, rows.map(_.reachableSources))
    )

    appendRow(
      builder,
      layout,
      RenderedRow(
        count = CountHeader,
        client = ClientHeader,
        relationship = fansi.Str(RelationshipHeader),
        introducedBy = IntroducedByHeader,
        usedClasses = fansi.Str(UsedClassesHeader),
        reachableClasses = fansi.Str(ReachableClassesHeader),
        reachableSources = fansi.Str(ReachableSourcesHeader)
      )
    )
    appendRow(
      builder,
      layout,
      RenderedRow(
        count = "-" * layout.countWidth,
        client = "-" * layout.clientWidth,
        relationship = fansi.Str("-" * layout.relationshipWidth),
        introducedBy = "-" * layout.introducedByWidth,
        usedClasses = fansi.Str("-" * layout.usedClassesWidth),
        reachableClasses = fansi.Str("-" * layout.reachableClassesWidth),
        reachableSources = fansi.Str("-" * layout.reachableSourcesWidth)
      )
    )
    rows.foreach(row => appendRow(builder, layout, row))
  }

  private def renderedRow(
      module: StrictDepsDownstreamUsageModule,
      index: Int
  ): RenderedRow = {
    RenderedRow(
      count = (index + 1).toString,
      client = display(module.moduleName),
      relationship = relationship(module.relationship),
      introducedBy = moduleList(module.introducedByModuleNames),
      usedClasses = countAndBar(module.usedClassCount, module.usedClassTotalCount, module.usedClassPercent),
      reachableClasses = countAndBar(
        module.reachableClassCount,
        module.reachableClassTotalCount,
        module.reachableClassPercent
      ),
      reachableSources = countAndBar(
        module.reachableSourceCount,
        module.reachableSourceTotalCount,
        module.reachableSourcePercent
      )
    )
  }

  private def appendRow(
      builder: StringBuilder,
      layout: TableLayout,
      row: RenderedRow
  ): Unit = {
    val rendered = new StringBuilder
    rendered.append(padLeft(row.count, layout.countWidth))
    rendered.append("  ")
    rendered.append(padRight(row.client, layout.clientWidth))
    rendered.append("  ")
    rendered.append(padRightFansi(row.relationship, layout.relationshipWidth))
    rendered.append("  ")
    rendered.append(padRight(row.introducedBy, layout.introducedByWidth))
    rendered.append("  ")
    rendered.append(padLeftFansi(row.usedClasses, layout.usedClassesWidth))
    rendered.append("  ")
    rendered.append(padLeftFansi(row.reachableClasses, layout.reachableClassesWidth))
    rendered.append("  ")
    rendered.append(padLeftFansi(row.reachableSources, layout.reachableSourcesWidth))
    builder.append(trimRight(rendered.result()))
    builder.append("\n")
  }

  private def moduleList(moduleNames: Seq[String]): String = {
    if (moduleNames.isEmpty) {
      ""
    } else if (moduleNames.size <= 3) {
      moduleNames.map(display).mkString(",")
    } else {
      moduleNames.take(3).map(display).mkString(",") + s",...+${moduleNames.size - 3}"
    }
  }

  private def relationship(value: String): fansi.Str = {
    if (value == "direct") {
      fansi.Color.Green("█") ++ fansi.Str(" direct")
    } else {
      fansi.Color.Blue("█") ++ fansi.Str(" transitive")
    }
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

  private def padLeftFansi(value: fansi.Str, width: Int): String = {
    (" " * math.max(0, width - value.length)) + value.render
  }

  private def padRightFansi(value: fansi.Str, width: Int): String = {
    value.render + (" " * math.max(0, width - value.length))
  }

  private def trimRight(value: String): String = {
    value.reverse.dropWhile(_.isWhitespace).reverse
  }

  private def display(value: String): String = {
    value
      .replace("\r", " ")
      .replace("\n", " ")
  }

  private final case class TableLayout(
      countWidth: Int,
      clientWidth: Int,
      relationshipWidth: Int,
      introducedByWidth: Int,
      usedClassesWidth: Int,
      reachableClassesWidth: Int,
      reachableSourcesWidth: Int
  )

  private final case class RenderedRow(
      count: String,
      client: String,
      relationship: fansi.Str,
      introducedBy: String,
      usedClasses: fansi.Str,
      reachableClasses: fansi.Str,
      reachableSources: fansi.Str
  )
}
