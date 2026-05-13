package io.github.nguyenyou.millstrictdeps

import mainargs.arg
import mill.*
import mill.api.DefaultTaskModule
import mill.api.Discover
import mill.api.ExternalModule
import mill.api.Result
import mill.util.Tasks

object strictDepsCommonAncestors extends ExternalModule with DefaultTaskModule {
  override def defaultTask(): String = {
    "commonAncestors"
  }

  def commonAncestors(
      @arg(positional = true) snapshots: Tasks[StrictDepsGraphSnapshot] =
        Tasks.resolveMainDefault("__.strictDepsGraphSnapshot"),
      limit: Int = 50
  ): Command[Unit] = Task.Command {
    val report = StrictDepsAnalyzer.commonAncestorReport(
      snapshots = Task.sequence(snapshots.value)()
    )

    Task.log.info(
      "\n" + StrictDepsCommonAncestorsRenderer.render(
        report = report,
        limit = limit
      )
    )
    Result.Success(())
  }

  lazy val millDiscover = Discover[this.type]
}
