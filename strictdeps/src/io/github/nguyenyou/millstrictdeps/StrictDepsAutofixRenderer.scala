package io.github.nguyenyou.millstrictdeps

object StrictDepsAutofixRenderer {
  def render(plan: StrictDepsAutofix.Plan, dryRun: Boolean): String = {
    val builder = new StringBuilder
    builder.append(s"# Strict Deps Autofix: ${escape(plan.moduleName)}\n\n")
    builder.append(s"Source file: `${escape(plan.sourceFile.toString)}`\n\n")

    if (dryRun) {
      builder.append("Mode: dry run. No files were changed.\n\n")
    } else if (plan.canApply && plan.hasChanges) {
      builder.append("Mode: apply. Source file can be updated.\n\n")
    } else {
      builder.append("Mode: apply. No files were changed.\n\n")
    }

    builder.append("## Edits\n\n")
    if (plan.edits.isEmpty) {
      builder.append("_None._\n\n")
    } else {
      plan.edits.foreach { edit =>
        val expression = edit.expression.map(value => s" as `$value`").getOrElse("")
        builder.append(
          s"- ${edit.action} `${escape(edit.moduleName)}` in `${edit.dependencyKind}`$expression.\n"
        )
      }
      builder.append("\n")
    }

    builder.append("## Refused\n\n")
    if (plan.skips.isEmpty) {
      builder.append("_None._\n")
    } else {
      plan.skips.foreach { skip =>
        val dependencyKind = skip.dependencyKind.map(value => s" in `$value`").getOrElse("")
        builder.append(
          s"- ${skip.action} `${escape(skip.moduleName)}`$dependencyKind: ${skip.reason}.\n"
        )
      }
    }

    builder.result()
  }

  private def escape(value: String): String = {
    value.replace("|", "\\|")
  }
}
