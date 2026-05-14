package io.github.nguyenyou.millstrictdeps

object StrictDepsWhoIntroducesRenderer {
  private val DirectDepHeader = "direct dep"
  private val PathHeader = "path"
  private val PathArrow = " -> "

  def render(
      moduleName: String,
      report: StrictDepsWhoIntroducesReport
  ): String = {
    val builder = new StringBuilder
    builder.append(s"target: ${report.target}\n\n")
    if (report.introducers.isEmpty) {
      builder.append(
        s"${report.target} is not introduced by any direct module dep of $moduleName\n"
      )
    } else {
      val rows = report.introducers.map { introducer =>
        (introducer.directModuleName, formatPath(introducer.path))
      }
      val directWidth = (DirectDepHeader.length +: rows.map(_._1.length)).max
      val pathWidth = (PathHeader.length +: rows.map(_._2.length)).max

      builder.append(padRight(DirectDepHeader, directWidth))
      builder.append("  ")
      builder.append(padRight(PathHeader, pathWidth))
      builder.append("\n")
      builder.append("-" * directWidth)
      builder.append("  ")
      builder.append("-" * pathWidth)
      builder.append("\n")
      rows.foreach { case (directName, path) =>
        builder.append(padRight(directName, directWidth))
        builder.append("  ")
        builder.append(path)
        builder.append("\n")
      }
    }
    builder.result()
  }

  private def formatPath(path: Seq[String]): String = {
    path.mkString(PathArrow)
  }

  private def padRight(value: String, width: Int): String = {
    value + (" " * (width - value.length))
  }
}
