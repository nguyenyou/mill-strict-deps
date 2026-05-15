package io.github.nguyenyou.millstrictdeps

import mainargs.arg
import mill.*
import mill.api.DefaultTaskModule
import mill.api.Discover
import mill.api.ExternalModule
import mill.api.Result
import mill.util.Tasks

object strictDepsDownstreamUsage extends ExternalModule with DefaultTaskModule {
  override def defaultTask(): String = {
    "downstreamUsage"
  }

  def downstreamUsage(
      target: String,
      @arg(positional = true) snapshots: Tasks[StrictDepsCompileWasteSnapshot] =
        Tasks.resolveMainDefault("__.strictDepsCompileWasteSnapshot"),
      limit: Int = 50
  ): Command[Unit] = Task.Command {
    val report = StrictDepsAnalyzer.downstreamUsageReport(
      targetModuleName = target,
      snapshots = Task.sequence(snapshots.value)()
    )

    Task.log.info(
      "\n" + StrictDepsDownstreamUsageRenderer.render(
        report = report,
        limit = limit
      )
    )
    Result.Success(())
  }

  lazy val millDiscover = Discover[this.type]
}
