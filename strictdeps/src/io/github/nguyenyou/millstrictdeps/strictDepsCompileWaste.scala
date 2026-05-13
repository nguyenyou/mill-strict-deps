package io.github.nguyenyou.millstrictdeps

import mainargs.arg
import mill.*
import mill.api.DefaultTaskModule
import mill.api.Discover
import mill.api.ExternalModule
import mill.api.Result
import mill.util.Tasks

object strictDepsCompileWaste extends ExternalModule with DefaultTaskModule {
  override def defaultTask(): String = {
    "compileWaste"
  }

  def compileWaste(
      @arg(positional = true) snapshots: Tasks[StrictDepsCompileWasteSnapshot] =
        Tasks.resolveMainDefault("__.strictDepsCompileWasteSnapshot"),
      limit: Int = 50
  ): Command[Unit] = Task.Command {
    val report = StrictDepsAnalyzer.compileWasteGlobalReport(
      snapshots = Task.sequence(snapshots.value)()
    )

    Task.log.info(
      "\n" + StrictDepsCompileWasteRenderer.renderGlobal(
        report = report,
        limit = limit
      )
    )
    Result.Success(())
  }

  lazy val millDiscover = Discover[this.type]
}
