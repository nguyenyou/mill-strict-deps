package io.github.nguyenyou.millstrictdeps

import scala.util.control.NonFatal

import mill.api.BuildCtx
import mill.api.PathRef
import sbt.internal.inc.FileAnalysisStore

private[millstrictdeps] object StrictDepsZincFile {

  def containsAnalysis(path: os.Path): Boolean = {
    try {
      BuildCtx.withFilesystemCheckerDisabled {
        val file = PathRef.toAbsFile(path)
        file.isFile && !FileAnalysisStore.binary(file).get().isEmpty
      }
    } catch {
      case NonFatal(_) => false
    }
  }

  def materialize(source: os.Path, destination: os.Path): PathRef = {
    val absoluteSource = os.Path(PathRef.toAbsNioPath(source))
    BuildCtx.withFilesystemCheckerDisabled {
      os.copy.over(
        from = absoluteSource,
        to = destination,
        createFolders = true
      )
    }
    PathRef(destination)
  }
}
