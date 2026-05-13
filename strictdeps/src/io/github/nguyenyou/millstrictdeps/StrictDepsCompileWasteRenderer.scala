package io.github.nguyenyou.millstrictdeps

object StrictDepsCompileWasteRenderer {
  private val MetricHeader = "metric"
  private val CountHeader = "count"
  private val ModuleHeader = "module"
  private val ClientHeader = "client"
  private val DependencyHeader = "dependency"
  private val RelationshipHeader = "relationship"
  private val IntroducedByHeader = "introduced by"
  private val NeededByHeader = "needed by"
  private val DirectByHeader = "direct by"
  private val DeltaHeader = "delta"
  private val ReachableDeltaHeader = "reachable delta"
  private val WastedDeltaHeader = "wasted delta"
  private val OwnWeightHeader = "own weight"
  private val ReachableSourcesHeader = "reachable sources"
  private val WastedOwnHeader = "wasted own"
  private val OwnClassesHeader = "own classes"
  private val ReachableClassesHeader = "reachable classes"

  def render(
      snapshot: StrictDepsCompileWasteSnapshot,
      limit: Int
  ): String = {
    val builder = new StringBuilder
    appendSnapshotSummary(builder, snapshot)

    if (snapshot.dependencies.isEmpty) {
      builder.append("No dependency waste rows were collected.\n")
    } else {
      val rows =
        if (limit <= 0) {
          snapshot.dependencies
        } else {
          snapshot.dependencies.take(limit)
        }
      appendDependencyTable(builder, rows)

      if (limit > 0 && snapshot.dependencies.size > limit) {
        builder.append(s"... ${snapshot.dependencies.size - limit} more dependencies\n")
      }
    }

    builder.result()
  }

  def renderGlobal(
      report: StrictDepsCompileWasteGlobalReport,
      limit: Int
  ): String = {
    val builder = new StringBuilder
    appendGlobalSummary(builder, report)

    if (report.badNodes.isEmpty) {
      builder.append("No compile-waste snapshots were collected.\n")
    } else {
      builder.append("bad nodes\n")
      appendNodeTable(builder, limited(report.badNodes, limit))
      if (limit > 0 && report.badNodes.size > limit) {
        builder.append(s"... ${report.badNodes.size - limit} more nodes\n")
      }
      builder.append("\n")

      builder.append("bad edges\n")
      appendEdgeTable(builder, limited(report.badEdges, limit))
      if (limit > 0 && report.badEdges.size > limit) {
        builder.append(s"... ${report.badEdges.size - limit} more edges\n")
      }
    }

    builder.result()
  }

  private def appendSnapshotSummary(
      builder: StringBuilder,
      snapshot: StrictDepsCompileWasteSnapshot
  ): Unit = {
    appendSummary(
      builder,
      Seq(
        "dependency sources" -> snapshot.dependencySourceCount.toString,
        "reachable dependency sources" -> snapshot.reachableDependencySourceCount.toString,
        "wasted dependency sources" -> snapshot.wastedDependencySourceCount.toString,
        "dependency classes" -> snapshot.dependencyClassCount.toString,
        "reachable dependency classes" -> snapshot.reachableDependencyClassCount.toString,
        "wasted dependency classes" -> snapshot.wastedDependencyClassCount.toString,
        "delta sources" -> snapshot.deltaSourceCount.toString,
        "reachable delta sources" -> snapshot.reachableDeltaSourceCount.toString,
        "wasted delta sources" -> formatWaste(
          value = snapshot.wastedDeltaSourceCount,
          percent = percent(snapshot.wastedDeltaSourceCount, snapshot.deltaSourceCount)
        )
      )
    )
  }

  private def appendGlobalSummary(
      builder: StringBuilder,
      report: StrictDepsCompileWasteGlobalReport
  ): Unit = {
    appendSummary(
      builder,
      Seq(
        "root modules" -> report.rootModuleCount.toString,
        "dependency modules" -> report.dependencyModuleCount.toString,
        "client dependency rows" -> report.dependencyEdgeCount.toString,
        "delta sources" -> report.totalDeltaSourceCount.toString,
        "reachable delta sources" -> report.totalReachableDeltaSourceCount.toString,
        "wasted delta sources" -> formatWaste(
          value = report.totalWastedDeltaSourceCount,
          percent = report.wastedDeltaSourcePercent
        )
      )
    )
  }

  private def appendSummary(
      builder: StringBuilder,
      rows: Seq[(String, String)]
  ): Unit = {
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

  private def appendDependencyTable(
      builder: StringBuilder,
      dependencies: Seq[StrictDepsCompileWasteDependency]
  ): Unit = {
    val rows = dependencies.map(renderedDependencyRow)
    val layout = DependencyLayout(
      moduleWidth = maxWidth(ModuleHeader +: rows.map(_.module)),
      relationshipWidth = maxWidth(RelationshipHeader +: rows.map(_.relationship)),
      introducedByWidth = maxWidth(IntroducedByHeader +: rows.map(_.introducedBy)),
      deltaWidth = maxWidth(DeltaHeader +: rows.map(_.delta)),
      reachableDeltaWidth = maxWidth(ReachableDeltaHeader +: rows.map(_.reachableDelta)),
      wastedDeltaWidth = maxWidth(WastedDeltaHeader +: rows.map(_.wastedDelta)),
      ownWeightWidth = maxWidth(OwnWeightHeader +: rows.map(_.ownWeight)),
      reachableSourcesWidth = maxWidth(ReachableSourcesHeader +: rows.map(_.reachableSources)),
      wastedOwnWidth = maxWidth(WastedOwnHeader +: rows.map(_.wastedOwn)),
      ownClassesWidth = maxWidth(OwnClassesHeader +: rows.map(_.ownClasses)),
      reachableClassesWidth = maxWidth(ReachableClassesHeader +: rows.map(_.reachableClasses))
    )

    appendDependencyRow(
      builder,
      layout,
      RenderedDependencyRow(
        module = ModuleHeader,
        relationship = RelationshipHeader,
        introducedBy = IntroducedByHeader,
        delta = DeltaHeader,
        reachableDelta = ReachableDeltaHeader,
        wastedDelta = WastedDeltaHeader,
        ownWeight = OwnWeightHeader,
        reachableSources = ReachableSourcesHeader,
        wastedOwn = WastedOwnHeader,
        ownClasses = OwnClassesHeader,
        reachableClasses = ReachableClassesHeader
      )
    )
    appendDependencyRow(
      builder,
      layout,
      RenderedDependencyRow(
        module = "-" * layout.moduleWidth,
        relationship = "-" * layout.relationshipWidth,
        introducedBy = "-" * layout.introducedByWidth,
        delta = "-" * layout.deltaWidth,
        reachableDelta = "-" * layout.reachableDeltaWidth,
        wastedDelta = "-" * layout.wastedDeltaWidth,
        ownWeight = "-" * layout.ownWeightWidth,
        reachableSources = "-" * layout.reachableSourcesWidth,
        wastedOwn = "-" * layout.wastedOwnWidth,
        ownClasses = "-" * layout.ownClassesWidth,
        reachableClasses = "-" * layout.reachableClassesWidth
      )
    )
    rows.foreach(row => appendDependencyRow(builder, layout, row))
  }

  private def appendNodeTable(
      builder: StringBuilder,
      nodes: Seq[StrictDepsCompileWasteNode]
  ): Unit = {
    val rows = nodes.map(renderedNodeRow)
    val layout = NodeLayout(
      moduleWidth = maxWidth(ModuleHeader +: rows.map(_.module)),
      neededByWidth = maxWidth(NeededByHeader +: rows.map(_.neededBy)),
      directByWidth = maxWidth(DirectByHeader +: rows.map(_.directBy)),
      deltaWidth = maxWidth(DeltaHeader +: rows.map(_.delta)),
      reachableDeltaWidth = maxWidth(ReachableDeltaHeader +: rows.map(_.reachableDelta)),
      wastedDeltaWidth = maxWidth(WastedDeltaHeader +: rows.map(_.wastedDelta)),
      ownWeightWidth = maxWidth(OwnWeightHeader +: rows.map(_.ownWeight)),
      reachableSourcesWidth = maxWidth(ReachableSourcesHeader +: rows.map(_.reachableSources)),
      wastedOwnWidth = maxWidth(WastedOwnHeader +: rows.map(_.wastedOwn)),
      ownClassesWidth = maxWidth(OwnClassesHeader +: rows.map(_.ownClasses)),
      reachableClassesWidth = maxWidth(ReachableClassesHeader +: rows.map(_.reachableClasses))
    )

    appendNodeRow(
      builder,
      layout,
      RenderedNodeRow(
        module = ModuleHeader,
        neededBy = NeededByHeader,
        directBy = DirectByHeader,
        delta = DeltaHeader,
        reachableDelta = ReachableDeltaHeader,
        wastedDelta = WastedDeltaHeader,
        ownWeight = OwnWeightHeader,
        reachableSources = ReachableSourcesHeader,
        wastedOwn = WastedOwnHeader,
        ownClasses = OwnClassesHeader,
        reachableClasses = ReachableClassesHeader
      )
    )
    appendNodeRow(
      builder,
      layout,
      RenderedNodeRow(
        module = "-" * layout.moduleWidth,
        neededBy = "-" * layout.neededByWidth,
        directBy = "-" * layout.directByWidth,
        delta = "-" * layout.deltaWidth,
        reachableDelta = "-" * layout.reachableDeltaWidth,
        wastedDelta = "-" * layout.wastedDeltaWidth,
        ownWeight = "-" * layout.ownWeightWidth,
        reachableSources = "-" * layout.reachableSourcesWidth,
        wastedOwn = "-" * layout.wastedOwnWidth,
        ownClasses = "-" * layout.ownClassesWidth,
        reachableClasses = "-" * layout.reachableClassesWidth
      )
    )
    rows.foreach(row => appendNodeRow(builder, layout, row))
  }

  private def appendEdgeTable(
      builder: StringBuilder,
      edges: Seq[StrictDepsCompileWasteEdge]
  ): Unit = {
    val rows = edges.map(renderedEdgeRow)
    val layout = EdgeLayout(
      clientWidth = maxWidth(ClientHeader +: rows.map(_.client)),
      dependencyWidth = maxWidth(DependencyHeader +: rows.map(_.dependency)),
      relationshipWidth = maxWidth(RelationshipHeader +: rows.map(_.relationship)),
      introducedByWidth = maxWidth(IntroducedByHeader +: rows.map(_.introducedBy)),
      deltaWidth = maxWidth(DeltaHeader +: rows.map(_.delta)),
      reachableDeltaWidth = maxWidth(ReachableDeltaHeader +: rows.map(_.reachableDelta)),
      wastedDeltaWidth = maxWidth(WastedDeltaHeader +: rows.map(_.wastedDelta)),
      ownWeightWidth = maxWidth(OwnWeightHeader +: rows.map(_.ownWeight)),
      reachableSourcesWidth = maxWidth(ReachableSourcesHeader +: rows.map(_.reachableSources)),
      wastedOwnWidth = maxWidth(WastedOwnHeader +: rows.map(_.wastedOwn)),
      ownClassesWidth = maxWidth(OwnClassesHeader +: rows.map(_.ownClasses)),
      reachableClassesWidth = maxWidth(ReachableClassesHeader +: rows.map(_.reachableClasses))
    )

    appendEdgeRow(
      builder,
      layout,
      RenderedEdgeRow(
        client = ClientHeader,
        dependency = DependencyHeader,
        relationship = RelationshipHeader,
        introducedBy = IntroducedByHeader,
        delta = DeltaHeader,
        reachableDelta = ReachableDeltaHeader,
        wastedDelta = WastedDeltaHeader,
        ownWeight = OwnWeightHeader,
        reachableSources = ReachableSourcesHeader,
        wastedOwn = WastedOwnHeader,
        ownClasses = OwnClassesHeader,
        reachableClasses = ReachableClassesHeader
      )
    )
    appendEdgeRow(
      builder,
      layout,
      RenderedEdgeRow(
        client = "-" * layout.clientWidth,
        dependency = "-" * layout.dependencyWidth,
        relationship = "-" * layout.relationshipWidth,
        introducedBy = "-" * layout.introducedByWidth,
        delta = "-" * layout.deltaWidth,
        reachableDelta = "-" * layout.reachableDeltaWidth,
        wastedDelta = "-" * layout.wastedDeltaWidth,
        ownWeight = "-" * layout.ownWeightWidth,
        reachableSources = "-" * layout.reachableSourcesWidth,
        wastedOwn = "-" * layout.wastedOwnWidth,
        ownClasses = "-" * layout.ownClassesWidth,
        reachableClasses = "-" * layout.reachableClassesWidth
      )
    )
    rows.foreach(row => appendEdgeRow(builder, layout, row))
  }

  private def renderedDependencyRow(
      dependency: StrictDepsCompileWasteDependency
  ): RenderedDependencyRow = {
    RenderedDependencyRow(
      module = display(dependency.moduleName),
      relationship = dependency.relationship,
      introducedBy = moduleList(dependency.introducedByModuleNames),
      delta = dependency.deltaSourceCount.toString,
      reachableDelta = dependency.reachableDeltaSourceCount.toString,
      wastedDelta = formatWaste(dependency.wastedDeltaSourceCount, dependency.wastedDeltaSourcePercent),
      ownWeight = dependency.ownSourceCount.toString,
      reachableSources = formatReachability(
        reached = dependency.reachableSourceCount,
        total = dependency.ownSourceCount,
        percent = dependency.reachableSourcePercent
      ),
      wastedOwn = dependency.wastedOwnSourceCount.toString,
      ownClasses = dependency.ownClassCount.toString,
      reachableClasses = formatReachability(
        reached = dependency.reachableClassCount,
        total = dependency.ownClassCount,
        percent = dependency.reachableClassPercent
      )
    )
  }

  private def renderedNodeRow(node: StrictDepsCompileWasteNode): RenderedNodeRow = {
    RenderedNodeRow(
      module = display(node.moduleName),
      neededBy = node.neededByModuleCount.toString,
      directBy = node.directNeededByModuleCount.toString,
      delta = node.totalDeltaSourceCount.toString,
      reachableDelta = node.totalReachableDeltaSourceCount.toString,
      wastedDelta = formatWaste(node.totalWastedDeltaSourceCount, node.wastedDeltaSourcePercent),
      ownWeight = node.totalOwnSourceCount.toString,
      reachableSources = formatReachability(
        reached = node.totalReachableSourceCount,
        total = node.totalOwnSourceCount,
        percent = node.reachableSourcePercent
      ),
      wastedOwn = node.totalWastedOwnSourceCount.toString,
      ownClasses = node.maxOwnClassCount.toString,
      reachableClasses = formatReachability(
        reached = node.totalReachableClassCount,
        total = node.totalReachableClassCount + node.totalWastedClassCount,
        percent = node.reachableClassPercent
      )
    )
  }

  private def renderedEdgeRow(edge: StrictDepsCompileWasteEdge): RenderedEdgeRow = {
    RenderedEdgeRow(
      client = display(edge.moduleName),
      dependency = display(edge.dependencyModuleName),
      relationship = edge.relationship,
      introducedBy = moduleList(edge.introducedByModuleNames),
      delta = edge.deltaSourceCount.toString,
      reachableDelta = edge.reachableDeltaSourceCount.toString,
      wastedDelta = formatWaste(edge.wastedDeltaSourceCount, edge.wastedDeltaSourcePercent),
      ownWeight = edge.ownSourceCount.toString,
      reachableSources = formatReachability(
        reached = edge.reachableSourceCount,
        total = edge.ownSourceCount,
        percent = edge.reachableSourcePercent
      ),
      wastedOwn = edge.wastedOwnSourceCount.toString,
      ownClasses = edge.ownClassCount.toString,
      reachableClasses = formatReachability(
        reached = edge.reachableClassCount,
        total = edge.ownClassCount,
        percent = edge.reachableClassPercent
      )
    )
  }

  private def appendDependencyRow(
      builder: StringBuilder,
      layout: DependencyLayout,
      row: RenderedDependencyRow
  ): Unit = {
    appendValues(
      builder,
      Seq(
        row.module -> layout.moduleWidth,
        row.relationship -> layout.relationshipWidth,
        row.introducedBy -> layout.introducedByWidth,
        row.delta -> layout.deltaWidth,
        row.reachableDelta -> layout.reachableDeltaWidth,
        row.wastedDelta -> layout.wastedDeltaWidth,
        row.ownWeight -> layout.ownWeightWidth,
        row.reachableSources -> layout.reachableSourcesWidth,
        row.wastedOwn -> layout.wastedOwnWidth,
        row.ownClasses -> layout.ownClassesWidth,
        row.reachableClasses -> layout.reachableClassesWidth
      ),
      leftAlignedIndexes = Set(0, 1, 2)
    )
  }

  private def appendNodeRow(
      builder: StringBuilder,
      layout: NodeLayout,
      row: RenderedNodeRow
  ): Unit = {
    appendValues(
      builder,
      Seq(
        row.module -> layout.moduleWidth,
        row.neededBy -> layout.neededByWidth,
        row.directBy -> layout.directByWidth,
        row.delta -> layout.deltaWidth,
        row.reachableDelta -> layout.reachableDeltaWidth,
        row.wastedDelta -> layout.wastedDeltaWidth,
        row.ownWeight -> layout.ownWeightWidth,
        row.reachableSources -> layout.reachableSourcesWidth,
        row.wastedOwn -> layout.wastedOwnWidth,
        row.ownClasses -> layout.ownClassesWidth,
        row.reachableClasses -> layout.reachableClassesWidth
      ),
      leftAlignedIndexes = Set(0)
    )
  }

  private def appendEdgeRow(
      builder: StringBuilder,
      layout: EdgeLayout,
      row: RenderedEdgeRow
  ): Unit = {
    appendValues(
      builder,
      Seq(
        row.client -> layout.clientWidth,
        row.dependency -> layout.dependencyWidth,
        row.relationship -> layout.relationshipWidth,
        row.introducedBy -> layout.introducedByWidth,
        row.delta -> layout.deltaWidth,
        row.reachableDelta -> layout.reachableDeltaWidth,
        row.wastedDelta -> layout.wastedDeltaWidth,
        row.ownWeight -> layout.ownWeightWidth,
        row.reachableSources -> layout.reachableSourcesWidth,
        row.wastedOwn -> layout.wastedOwnWidth,
        row.ownClasses -> layout.ownClassesWidth,
        row.reachableClasses -> layout.reachableClassesWidth
      ),
      leftAlignedIndexes = Set(0, 1, 2, 3)
    )
  }

  private def appendValues(
      builder: StringBuilder,
      values: Seq[(String, Int)],
      leftAlignedIndexes: Set[Int]
  ): Unit = {
    val row = new StringBuilder
    values.zipWithIndex.foreach { case ((value, width), index) =>
      if (index > 0) {
        row.append("  ")
      }
      if (leftAlignedIndexes.contains(index)) {
        row.append(padRight(value, width))
      } else {
        row.append(padLeft(value, width))
      }
    }
    builder.append(trimRight(row.result()))
    builder.append("\n")
  }

  private def limited[A](values: Seq[A], limit: Int): Seq[A] = {
    if (limit <= 0) {
      values
    } else {
      values.take(limit)
    }
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

  private def formatReachability(
      reached: Int,
      total: Int,
      percent: Double
  ): String = {
    if (reached == 0) {
      "zero"
    } else {
      s"$reached / $total (${formatPercent(percent)})"
    }
  }

  private def formatWaste(
      value: Int,
      percent: Double
  ): String = {
    if (value == 0) {
      "zero"
    } else {
      s"$value (${formatPercent(percent)})"
    }
  }

  private def percent(numerator: Int, denominator: Int): Double = {
    if (denominator == 0) {
      0.0
    } else {
      math.round(numerator.toDouble * 1000.0 / denominator).toDouble / 10.0
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

  private final case class DependencyLayout(
      moduleWidth: Int,
      relationshipWidth: Int,
      introducedByWidth: Int,
      deltaWidth: Int,
      reachableDeltaWidth: Int,
      wastedDeltaWidth: Int,
      ownWeightWidth: Int,
      reachableSourcesWidth: Int,
      wastedOwnWidth: Int,
      ownClassesWidth: Int,
      reachableClassesWidth: Int
  )

  private final case class NodeLayout(
      moduleWidth: Int,
      neededByWidth: Int,
      directByWidth: Int,
      deltaWidth: Int,
      reachableDeltaWidth: Int,
      wastedDeltaWidth: Int,
      ownWeightWidth: Int,
      reachableSourcesWidth: Int,
      wastedOwnWidth: Int,
      ownClassesWidth: Int,
      reachableClassesWidth: Int
  )

  private final case class EdgeLayout(
      clientWidth: Int,
      dependencyWidth: Int,
      relationshipWidth: Int,
      introducedByWidth: Int,
      deltaWidth: Int,
      reachableDeltaWidth: Int,
      wastedDeltaWidth: Int,
      ownWeightWidth: Int,
      reachableSourcesWidth: Int,
      wastedOwnWidth: Int,
      ownClassesWidth: Int,
      reachableClassesWidth: Int
  )

  private final case class RenderedDependencyRow(
      module: String,
      relationship: String,
      introducedBy: String,
      delta: String,
      reachableDelta: String,
      wastedDelta: String,
      ownWeight: String,
      reachableSources: String,
      wastedOwn: String,
      ownClasses: String,
      reachableClasses: String
  )

  private final case class RenderedNodeRow(
      module: String,
      neededBy: String,
      directBy: String,
      delta: String,
      reachableDelta: String,
      wastedDelta: String,
      ownWeight: String,
      reachableSources: String,
      wastedOwn: String,
      ownClasses: String,
      reachableClasses: String
  )

  private final case class RenderedEdgeRow(
      client: String,
      dependency: String,
      relationship: String,
      introducedBy: String,
      delta: String,
      reachableDelta: String,
      wastedDelta: String,
      ownWeight: String,
      reachableSources: String,
      wastedOwn: String,
      ownClasses: String,
      reachableClasses: String
  )
}
