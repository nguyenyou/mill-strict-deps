package io.github.nguyenyou.millstrictdeps

import mill.api.Segment
import mill.api.Segments

import scala.collection.mutable

object StrictDepsAutofix {
  final case class ModuleRef(
      moduleName: String,
      segments: Segments,
      directDependencyModuleNames: Seq[String]
  ) {
    def hasCrossSegments: Boolean = {
      segments.value.exists {
        case _: Segment.Cross => true
        case _: Segment.Label => false
      }
    }
  }

  final case class Input(
      moduleName: String,
      moduleSegments: Segments,
      sourceFile: os.Path,
      moduleLine: Int,
      normalDirectDeps: Seq[ModuleRef],
      compileDirectDeps: Seq[ModuleRef],
      transitiveDeps: Seq[ModuleRef],
      missingDirectDeps: Seq[String],
      unusedDirectDeps: Seq[String]
  )

  final case class Plan(
      moduleName: String,
      sourceFile: os.Path,
      edits: Seq[Edit],
      skips: Seq[Skip],
      replacements: Seq[Replacement]
  ) {
    def hasChanges: Boolean = edits.nonEmpty
    def canApply: Boolean = skips.isEmpty

    def applyTo(source: String): String = {
      replacements
        .sortBy(replacement => -replacement.start)
        .foldLeft(source) { (current, replacement) =>
          current.substring(0, replacement.start) + replacement.text + current.substring(replacement.end)
        }
    }
  }

  final case class Edit(
      action: String,
      dependencyKind: String,
      moduleName: String,
      expression: Option[String],
      message: String
  )

  final case class Skip(
      action: String,
      dependencyKind: Option[String],
      moduleName: String,
      reason: String
  )

  final case class Replacement(start: Int, end: Int, text: String)

  private final case class ModuleBody(openBrace: Int, closeBrace: Int, indent: String)

  private final case class DependencyMethod(
      name: String,
      seqStart: Int,
      seqEnd: Int,
      args: Seq[String],
      multiline: Boolean,
      itemIndent: String,
      closeIndent: String,
      prefixKind: PrefixKind,
      suffixKind: SuffixKind
  )

  private enum PrefixKind {
    case Empty
    case SuperPlus
  }

  private enum SuffixKind {
    case Empty
    case PlusSuper
  }

  private final case class MethodEditState(
      dependencyKind: String,
      methodName: String,
      method: Option[DependencyMethod],
      methodProblem: Option[String],
      additions: Seq[AddRequest],
      removals: Seq[RemoveRequest]
  )

  private final case class AddRequest(module: ModuleRef, expression: String)
  private final case class RemoveRequest(module: ModuleRef)

  private enum DependencyMethodSearch {
    case Missing
    case Found(method: DependencyMethod)
    case Unsupported(reason: String)
  }

  def plan(input: Input, source: String): Plan = {
    val code = codeMask(source)
    findModuleBody(source, code, input.moduleLine) match {
      case Left(reason) =>
        Plan(
          moduleName = input.moduleName,
          sourceFile = input.sourceFile,
          edits = Seq.empty,
          skips = (input.missingDirectDeps.map(moduleName =>
            Skip("add", Some("moduleDeps"), moduleName, reason)
          ) ++ input.unusedDirectDeps.map(moduleName =>
            Skip("remove", None, moduleName, reason)
          )).distinct,
          replacements = Seq.empty
        )
      case Right(body) =>
        planInBody(input, source, code, body)
    }
  }

  private def planInBody(
      input: Input,
      source: String,
      code: Array[Boolean],
      body: ModuleBody
  ): Plan = {
    val refsByName = input.transitiveDeps.map(ref => ref.moduleName -> ref).toMap
    val normalByName = input.normalDirectDeps.map(ref => ref.moduleName -> ref).toMap
    val compileByName = input.compileDirectDeps.map(ref => ref.moduleName -> ref).toMap
    val graph = input.transitiveDeps.map(ref => ref.moduleName -> ref.directDependencyModuleNames.toSet).toMap
    val importedBuildPrefixes = buildWildcardImportPrefixes(source, code)

    val addPairs = input.missingDirectDeps.distinct.map { moduleName =>
      refsByName.get(moduleName) match {
        case None =>
          Left(Skip("add", Some("moduleDeps"), moduleName, "module metadata was not available"))
        case Some(ref) if ref.hasCrossSegments =>
          Left(Skip(
            "add",
            Some("moduleDeps"),
            moduleName,
            "cross module source expressions cannot be synthesized safely"
          ))
        case Some(ref) =>
          val dependencyKind = additionKind(moduleName, input.normalDirectDeps, input.compileDirectDeps, graph)
          sourceExpression(input.moduleSegments, ref.segments, importedBuildPrefixes) match {
            case None =>
              Left(Skip(
                "add",
                Some(dependencyKind),
                moduleName,
                "module path contains segments that cannot be rendered as a safe Scala expression"
              ))
            case Some(expression) =>
              Right(dependencyKind -> AddRequest(ref, expression))
          }
      }
    }

    val removePairs = input.unusedDirectDeps.distinct.map { moduleName =>
      (normalByName.get(moduleName), compileByName.get(moduleName)) match {
        case (Some(_), Some(_)) =>
          Left(Skip(
            "remove",
            None,
            moduleName,
            "dependency is declared in both moduleDeps and compileModuleDeps"
          ))
        case (None, None) =>
          Left(Skip("remove", None, moduleName, "direct dependency metadata was not available"))
        case (Some(ref), None) if ref.hasCrossSegments =>
          Left(Skip(
            "remove",
            Some("moduleDeps"),
            moduleName,
            "cross module source expressions cannot be matched safely"
          ))
        case (None, Some(ref)) if ref.hasCrossSegments =>
          Left(Skip(
            "remove",
            Some("compileModuleDeps"),
            moduleName,
            "cross module source expressions cannot be matched safely"
          ))
        case (Some(ref), None) =>
          Right("moduleDeps" -> RemoveRequest(ref))
        case (None, Some(ref)) =>
          Right("compileModuleDeps" -> RemoveRequest(ref))
      }
    }

    val initialSkips =
      addPairs.collect { case Left(skip) => skip } ++ removePairs.collect { case Left(skip) => skip }
    val additionsByKind = addPairs.collect { case Right((kind, request)) => kind -> request }.groupMap(_._1)(_._2)
    val removalsByKind = removePairs.collect { case Right((kind, request)) => kind -> request }.groupMap(_._1)(_._2)
    val methodNames = Seq("moduleDeps", "compileModuleDeps")
    val methodStates = methodNames.map { methodName =>
      val search = findDependencyMethod(source, code, body, methodName)
      MethodEditState(
        dependencyKind = methodName,
        methodName = methodName,
        method = search match {
          case DependencyMethodSearch.Found(method) => Some(method)
          case DependencyMethodSearch.Missing | DependencyMethodSearch.Unsupported(_) => None
        },
        methodProblem = search match {
          case DependencyMethodSearch.Unsupported(reason) => Some(reason)
          case DependencyMethodSearch.Missing | DependencyMethodSearch.Found(_) => None
        },
        additions = additionsByKind.getOrElse(methodName, Seq.empty),
        removals = removalsByKind.getOrElse(methodName, Seq.empty)
      )
    }

    val methodResults = methodStates.map { state =>
      planMethodEdit(source, body, input.moduleSegments, importedBuildPrefixes, state)
    }
    val edits = methodResults.flatMap(_._1)
    val skips = initialSkips ++ methodResults.flatMap(_._2)
    val replacements = methodResults.flatMap(_._3)

    Plan(
      moduleName = input.moduleName,
      sourceFile = input.sourceFile,
      edits = edits,
      skips = skips,
      replacements = replacements
    )
  }

  private def planMethodEdit(
      source: String,
      body: ModuleBody,
      currentSegments: Segments,
      importedBuildPrefixes: Seq[Seq[String]],
      state: MethodEditState
  ): (Seq[Edit], Seq[Skip], Seq[Replacement]) = {
    if (state.additions.isEmpty && state.removals.isEmpty) {
      (Seq.empty, Seq.empty, Seq.empty)
    } else {
      state.method match {
        case None if state.methodProblem.nonEmpty =>
          val skips = (state.additions.map(request => "add" -> request.module.moduleName) ++
            state.removals.map(request => "remove" -> request.module.moduleName)).map { case (action, moduleName) =>
            Skip(action, Some(state.dependencyKind), moduleName, state.methodProblem.get)
          }
          (Seq.empty, skips, Seq.empty)
        case None if state.removals.nonEmpty =>
          val skips = state.removals.map { request =>
            Skip(
              "remove",
              Some(state.dependencyKind),
              request.module.moduleName,
              s"${state.methodName} is not defined in the module body"
            )
          }
          (Seq.empty, skips, Seq.empty)
        case None =>
          val args = state.additions.map(_.expression).distinct.sorted
          val replacement = insertedMethod(source, body, state.methodName, args)
          val edits = state.additions.map { request =>
            Edit(
              action = "add",
              dependencyKind = state.dependencyKind,
              moduleName = request.module.moduleName,
              expression = Some(request.expression),
              message = s"add ${request.expression} to ${state.methodName}"
            )
          }
          (edits, Seq.empty, Seq(replacement))
        case Some(method) =>
          val removalResult = removeArgs(method.args, state.removals, currentSegments, importedBuildPrefixes)
          val removalSkips = removalResult._1
          val keptArgs = removalResult._2
          val existingNormalized = keptArgs.map(normalizeExpression).toSet
          val additions = state.additions.filterNot(request =>
            existingNormalized.contains(normalizeExpression(request.expression))
          )
          val nextArgs = (keptArgs ++ additions.map(_.expression)).distinct
          val replacementText = renderSeq(method, nextArgs)
          val replacement =
            if (replacementText == source.substring(method.seqStart, method.seqEnd)) {
              Seq.empty
            } else {
              Seq(Replacement(method.seqStart, method.seqEnd, replacementText))
            }
          val removeEdits = state.removals.filterNot(request =>
            removalSkips.exists(skip => skip.moduleName == request.module.moduleName)
          ).map { request =>
            Edit(
              action = "remove",
              dependencyKind = state.dependencyKind,
              moduleName = request.module.moduleName,
              expression = None,
              message = s"remove ${request.module.moduleName} from ${state.methodName}"
            )
          }
          val addEdits = additions.map { request =>
            Edit(
              action = "add",
              dependencyKind = state.dependencyKind,
              moduleName = request.module.moduleName,
              expression = Some(request.expression),
              message = s"add ${request.expression} to ${state.methodName}"
            )
          }
          (removeEdits ++ addEdits, removalSkips, replacement)
      }
    }
  }

  private def removeArgs(
      args: Seq[String],
      removals: Seq[RemoveRequest],
      currentSegments: Segments,
      importedBuildPrefixes: Seq[Seq[String]]
  ): (Seq[Skip], Seq[String]) = {
    val removeCandidateSets = removals.map { request =>
      request.module.moduleName -> removalCandidates(request.module, currentSegments, importedBuildPrefixes)
    }
    val matchedModules = mutable.Set.empty[String]
    val kept = Seq.newBuilder[String]

    args.foreach { arg =>
      val normalized = normalizeExpression(arg)
      val matches = removeCandidateSets.collect {
        case (moduleName, candidates) if candidates.contains(normalized) => moduleName
      }
      matches.distinct match {
        case Seq(moduleName) =>
          matchedModules += moduleName
        case Seq() =>
          kept += arg
        case _ =>
          kept += arg
      }
    }

    val skips = removals.flatMap { request =>
      if (matchedModules.contains(request.module.moduleName)) {
        None
      } else {
        Some(Skip(
          "remove",
          None,
          request.module.moduleName,
          "no exact dependency expression was found in the supported Seq(...) call"
        ))
      }
    }

    (skips, kept.result())
  }

  private def insertedMethod(
      source: String,
      body: ModuleBody,
      methodName: String,
      args: Seq[String]
  ): Replacement = {
    val methodIndent = body.indent + "  "
    val argsText =
      if (args.size <= 2) {
        s"Seq(${args.mkString(", ")})"
      } else {
        args.map(arg => methodIndent + "  " + arg).mkString("Seq(\n", ",\n", s"\n$methodIndent)")
      }
    val beforeClose = source.substring(0, body.closeBrace)
    val needsLeadingNewline = beforeClose.nonEmpty && !beforeClose.endsWith("\n")
    val leading = if (needsLeadingNewline) "\n" else ""
    val text = s"${leading}${methodIndent}override def $methodName = $argsText\n"
    Replacement(body.closeBrace, body.closeBrace, text)
  }

  private def renderSeq(method: DependencyMethod, args: Seq[String]): String = {
    val seqText =
      if (args.isEmpty) {
        "Seq.empty"
      } else if (method.multiline) {
        args.mkString(s"Seq(\n${method.itemIndent}", s",\n${method.itemIndent}", s"\n${method.closeIndent})")
      } else {
        s"Seq(${args.mkString(", ")})"
      }

    val withPrefix = method.prefixKind match {
      case PrefixKind.Empty => seqText
      case PrefixKind.SuperPlus => s"super.${method.name} ++ $seqText"
    }
    method.suffixKind match {
      case SuffixKind.Empty => withPrefix
      case SuffixKind.PlusSuper => s"$withPrefix ++ super.${method.name}"
    }
  }

  private def additionKind(
      moduleName: String,
      normalDirectDeps: Seq[ModuleRef],
      compileDirectDeps: Seq[ModuleRef],
      graph: Map[String, Set[String]]
  ): String = {
    val normalIntroducers = normalDirectDeps.exists(ref => dependencyClosure(ref.moduleName, graph).contains(moduleName))
    val compileIntroducers = compileDirectDeps.exists(ref => dependencyClosure(ref.moduleName, graph).contains(moduleName))
    if (normalIntroducers || !compileIntroducers) {
      "moduleDeps"
    } else {
      "compileModuleDeps"
    }
  }

  private def dependencyClosure(moduleName: String, graph: Map[String, Set[String]]): Set[String] = {
    val seen = mutable.Set.empty[String]
    val queue = mutable.Queue(moduleName)
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      if (!seen.contains(current)) {
        seen += current
        graph.getOrElse(current, Set.empty).toSeq.sorted.foreach(queue.enqueue(_))
      }
    }
    seen.toSet
  }

  private def sourceExpression(
      currentSegments: Segments,
      targetSegments: Segments,
      importedBuildPrefixes: Seq[Seq[String]]
  ): Option[String] = {
    labelsOnly(targetSegments).map { targetLabels =>
      val currentParent = labelsOnly(currentSegments).map(_.dropRight(1)).getOrElse(Seq.empty)
      val targetParent = targetLabels.dropRight(1)
      if (targetParent == currentParent && targetLabels.nonEmpty) {
        backtickWrap(targetLabels.last)
      } else {
        importedBuildExpressions(targetLabels, importedBuildPrefixes).headOption.getOrElse {
          ("build" +: targetLabels.map(backtickWrap)).mkString(".")
        }
      }
    }
  }

  private def labelsOnly(segments: Segments): Option[Seq[String]] = {
    val labels = Seq.newBuilder[String]
    var valid = true
    segments.value.foreach {
      case Segment.Label(value) =>
        labels += value
      case _: Segment.Cross =>
        valid = false
    }
    Option.when(valid)(labels.result())
  }

  private def buildWildcardImportPrefixes(source: String, code: Array[Boolean]): Seq[Seq[String]] = {
    val prefixes = Seq.newBuilder[Seq[String]]
    var start = 0
    while (start < source.length) {
      val end = lineEnd(source, start)
      parseBuildWildcardImport(codeSlice(source, code, start, end).trim).foreach(prefixes += _)
      start = end + 1
    }
    prefixes.result().distinct
  }

  private def parseBuildWildcardImport(line: String): Option[Seq[String]] = {
    val importPrefix = "import "
    if (line.startsWith(importPrefix)) {
      val imported = line.stripPrefix(importPrefix).filterNot(_.isWhitespace)
      val buildPrefix = "build."
      val wildcardTarget =
        if (imported.startsWith(buildPrefix) && imported.endsWith(".*")) {
          Some(imported.stripPrefix(buildPrefix).stripSuffix(".*"))
        } else if (imported.startsWith(buildPrefix) && imported.endsWith("._")) {
          Some(imported.stripPrefix(buildPrefix).stripSuffix("._"))
        } else {
          None
        }
      wildcardTarget.flatMap { target =>
        val segments = target.split('.').toSeq.filter(_.nonEmpty).map(unbacktick)
        Option.when(segments.nonEmpty)(segments)
      }
    } else {
      None
    }
  }

  private def unbacktick(value: String): String = {
    if (value.length >= 2 && value.head == '`' && value.last == '`') {
      value.substring(1, value.length - 1).replace("\\`", "`")
    } else {
      value
    }
  }

  private def removalCandidates(
      module: ModuleRef,
      currentSegments: Segments,
      importedBuildPrefixes: Seq[Seq[String]]
  ): Set[String] = {
    (labelsOnly(module.segments), labelsOnly(currentSegments)) match {
      case (None, _) => Set.empty
      case (Some(labels), _) if labels.isEmpty => Set.empty
      case (Some(labels), currentLabels) =>
        val currentParent = currentLabels.map(_.dropRight(1)).getOrElse(Seq.empty)
        val targetParent = labels.dropRight(1)
        val full = ("build" +: labels.map(backtickWrap)).mkString(".")
        val fromRoot = labels.map(backtickWrap).mkString(".")
        val candidates =
          if (targetParent == currentParent) {
            Seq(full, fromRoot, backtickWrap(labels.last))
          } else {
            Seq(full, fromRoot)
          }
        (candidates ++ importedBuildExpressions(labels, importedBuildPrefixes)).map(normalizeExpression).toSet
    }
  }

  private def importedBuildExpressions(
      targetLabels: Seq[String],
      importedBuildPrefixes: Seq[Seq[String]]
  ): Seq[String] = {
    importedBuildPrefixes.flatMap { prefix =>
      Option.when(targetLabels.length > prefix.length && targetLabels.take(prefix.length) == prefix) {
        targetLabels.drop(prefix.length).map(backtickWrap).mkString(".")
      }
    }.distinct.sortBy(expression => (expression.count(_ == '.'), expression))
  }

  private def backtickWrap(value: String): String = {
    if (isPlainIdentifier(value)) {
      value
    } else {
      "`" + value.replace("`", "\\`") + "`"
    }
  }

  private def isPlainIdentifier(value: String): Boolean = {
    value.nonEmpty &&
    (value.head.isLetter || value.head == '_') &&
    value.forall(ch => ch.isLetterOrDigit || ch == '_')
  }

  private def findModuleBody(
      source: String,
      code: Array[Boolean],
      lineNum: Int
  ): Either[String, ModuleBody] = {
    lineStart(source, lineNum) match {
      case None =>
        Left(s"module source line $lineNum is outside the source file")
      case Some(start) =>
        val indent = source.substring(start, lineEnd(source, start)).takeWhile(ch => ch == ' ' || ch == '\t')
        findOpeningBrace(source, code, start, indent.length) match {
          case None =>
            Left("module body is not a supported brace-delimited block")
          case Some(open) =>
            matchingDelimiter(source, code, open, '{', '}') match {
              case None => Left("module body braces could not be matched safely")
              case Some(close) => Right(ModuleBody(open, close, indent))
            }
        }
    }
  }

  private def findOpeningBrace(
      source: String,
      code: Array[Boolean],
      start: Int,
      moduleIndent: Int
  ): Option[Int] = {
    var i = start
    var parenDepth = 0
    var bracketDepth = 0
    var found: Option[Int] = None
    var done = false
    while (i < source.length && found.isEmpty && !done) {
      if (code(i)) {
        source.charAt(i) match {
          case '(' => parenDepth += 1
          case ')' => parenDepth = (parenDepth - 1).max(0)
          case '[' => bracketDepth += 1
          case ']' => bracketDepth = (bracketDepth - 1).max(0)
          case '{' if parenDepth == 0 && bracketDepth == 0 =>
            found = Some(i)
          case '\n' if parenDepth == 0 && bracketDepth == 0 =>
            val next = nextNonWhitespace(source, i + 1)
            val nextIndent = next.map(index => index - lineStartAt(source, index)).getOrElse(Int.MaxValue)
            if (next.exists(index => nextIndent <= moduleIndent && startsMember(source, code, index))) {
              done = true
            }
          case _ =>
        }
      }
      i += 1
    }
    found
  }

  private def findDependencyMethod(
      source: String,
      code: Array[Boolean],
      body: ModuleBody,
      methodName: String
  ): DependencyMethodSearch = {
    var i = body.openBrace + 1
    var braceDepth = 1
    var found: DependencyMethodSearch = DependencyMethodSearch.Missing
    while (i < body.closeBrace && found == DependencyMethodSearch.Missing) {
      if (code(i)) {
        source.charAt(i) match {
          case '{' => braceDepth += 1
          case '}' => braceDepth -= 1
          case _ if braceDepth == 1 && tokenAt(source, code, i, "def") =>
            readIdentifier(source, code, skipWhitespace(source, code, i + 3)) match {
              case Some((name, _)) if name == methodName =>
                found = parseDependencyMethod(source, code, body, i, methodName) match {
                  case Some(method) => DependencyMethodSearch.Found(method)
                  case None =>
                    DependencyMethodSearch.Unsupported(
                      s"$methodName is not a supported Seq(...), Seq.empty, Nil, super.$methodName ++ Seq(...), or Seq(...) ++ super.$methodName shape"
                    )
                }
              case _ =>
            }
          case _ =>
        }
      }
      i += 1
    }
    found
  }

  private def parseDependencyMethod(
      source: String,
      code: Array[Boolean],
      body: ModuleBody,
      defStart: Int,
      methodName: String
  ): Option[DependencyMethod] = {
    val equalsIndex = findEquals(source, code, defStart, body.closeBrace)
    equalsIndex.flatMap { eq =>
      val expressionStart = skipWhitespace(source, code, eq + 1)
      val expressionEnd = findExpressionEnd(source, code, expressionStart, body.closeBrace)
      parseSeqRegion(source, code, expressionStart, expressionEnd).flatMap { seqRegion =>
        val prefix = normalizeExpression(codeSlice(source, code, expressionStart, seqRegion._1))
        val suffix = normalizeExpression(codeSlice(source, code, seqRegion._2, expressionEnd))
        val prefixKind = prefix match {
          case "" => Some(PrefixKind.Empty)
          case value if value == s"super.$methodName++" => Some(PrefixKind.SuperPlus)
          case _ => None
        }
        val suffixKind = suffix match {
          case "" => Some(SuffixKind.Empty)
          case value if value == s"++super.$methodName" => Some(SuffixKind.PlusSuper)
          case _ => None
        }
        for {
          pk <- prefixKind
          sk <- suffixKind
        } yield {
          val args = seqRegion._3
          val multiline = source.substring(seqRegion._1, seqRegion._2).contains("\n")
          val closeIndent = lineIndent(source, seqRegion._2)
          val itemIndent = inferredItemIndent(source, seqRegion._1, seqRegion._2, closeIndent)
          DependencyMethod(
            name = methodName,
            seqStart = expressionStart,
            seqEnd = expressionEnd,
            args = args,
            multiline = multiline,
            itemIndent = itemIndent,
            closeIndent = closeIndent,
            prefixKind = pk,
            suffixKind = sk
          )
        }
      }
    }
  }

  private def parseSeqRegion(
      source: String,
      code: Array[Boolean],
      start: Int,
      end: Int
  ): Option[(Int, Int, Seq[String])] = {
    var i = skipWhitespace(source, code, start)
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0
    var found: Option[(Int, Int, Seq[String])] = None
    while (i < end && found.isEmpty) {
      if (code(i)) {
        if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
          found = parseSeqRegionAt(source, code, i, end)
        }
        if (found.isEmpty) {
          source.charAt(i) match {
            case '(' => parenDepth += 1
            case ')' => parenDepth = (parenDepth - 1).max(0)
            case '[' => bracketDepth += 1
            case ']' => bracketDepth = (bracketDepth - 1).max(0)
            case '{' => braceDepth += 1
            case '}' => braceDepth = (braceDepth - 1).max(0)
            case _ =>
          }
        }
      }
      i += 1
    }
    found
  }

  private def parseSeqRegionAt(
      source: String,
      code: Array[Boolean],
      first: Int,
      end: Int
  ): Option[(Int, Int, Seq[String])] = {
    if (tokenAt(source, code, first, "Seq")) {
      val afterSeq = skipWhitespace(source, code, first + 3)
      if (afterSeq < end && source.charAt(afterSeq) == '(' && code(afterSeq)) {
        matchingDelimiter(source, code, afterSeq, '(', ')').filter(_ < end).map { close =>
          (first, close + 1, splitArgs(source.substring(afterSeq + 1, close)))
        }
      } else if (
        afterSeq + ".empty".length <= end &&
        source.substring(afterSeq, afterSeq + ".empty".length) == ".empty"
      ) {
        Some((first, afterSeq + ".empty".length, Seq.empty))
      } else {
        None
      }
    } else if (tokenAt(source, code, first, "Nil")) {
      Some((first, first + 3, Seq.empty))
    } else {
      None
    }
  }

  private def splitArgs(value: String): Seq[String] = {
    val mask = codeMask(value)
    val args = Seq.newBuilder[String]
    var start = 0
    var i = 0
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0
    var inBacktick = false
    while (i < value.length) {
      val ch = value.charAt(i)
      if (mask(i)) {
        if (ch == '`') {
          inBacktick = !inBacktick
        } else if (!inBacktick) {
          ch match {
            case '(' => parenDepth += 1
            case ')' => parenDepth = (parenDepth - 1).max(0)
            case '[' => bracketDepth += 1
            case ']' => bracketDepth = (bracketDepth - 1).max(0)
            case '{' => braceDepth += 1
            case '}' => braceDepth = (braceDepth - 1).max(0)
            case ',' if parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 =>
              val arg = value.substring(start, i).trim
              if (arg.nonEmpty) {
                args += arg
              }
              start = i + 1
            case _ =>
          }
        }
      }
      i += 1
    }
    val last = value.substring(start).trim
    if (last.nonEmpty) {
      args += last
    }
    args.result()
  }

  private def findEquals(
      source: String,
      code: Array[Boolean],
      start: Int,
      end: Int
  ): Option[Int] = {
    var i = start
    var parenDepth = 0
    var bracketDepth = 0
    var found: Option[Int] = None
    while (i < end && found.isEmpty) {
      if (code(i)) {
        source.charAt(i) match {
          case '(' => parenDepth += 1
          case ')' => parenDepth = (parenDepth - 1).max(0)
          case '[' => bracketDepth += 1
          case ']' => bracketDepth = (bracketDepth - 1).max(0)
          case '=' if parenDepth == 0 && bracketDepth == 0 =>
            found = Some(i)
          case '\n' if parenDepth == 0 && bracketDepth == 0 =>
            if (nextNonWhitespace(source, i + 1).exists(index => startsMember(source, code, index))) {
              found = None
              i = end
            }
          case _ =>
        }
      }
      i += 1
    }
    found
  }

  private def findExpressionEnd(
      source: String,
      code: Array[Boolean],
      start: Int,
      bodyClose: Int
  ): Int = {
    var i = start
    var parenDepth = 0
    var bracketDepth = 0
    var braceDepth = 0
    var end = bodyClose
    var done = false
    while (i < bodyClose && !done) {
      if (code(i)) {
        source.charAt(i) match {
          case '(' => parenDepth += 1
          case ')' => parenDepth = (parenDepth - 1).max(0)
          case '[' => bracketDepth += 1
          case ']' => bracketDepth = (bracketDepth - 1).max(0)
          case '{' => braceDepth += 1
          case '}' if braceDepth > 0 => braceDepth -= 1
          case '}' if parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 =>
            end = i
            done = true
          case '\n' if parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 =>
            nextNonWhitespace(source, i + 1) match {
              case Some(next) if startsMember(source, code, next) || source.charAt(next) == '}' =>
                end = i
                done = true
              case _ =>
            }
          case _ =>
        }
      }
      i += 1
    }
    end
  }

  private def matchingDelimiter(
      source: String,
      code: Array[Boolean],
      open: Int,
      openChar: Char,
      closeChar: Char
  ): Option[Int] = {
    var i = open
    var depth = 0
    var found: Option[Int] = None
    while (i < source.length && found.isEmpty) {
      if (code(i)) {
        val ch = source.charAt(i)
        if (ch == openChar) {
          depth += 1
        } else if (ch == closeChar) {
          depth -= 1
          if (depth == 0) {
            found = Some(i)
          }
        }
      }
      i += 1
    }
    found
  }

  private def codeMask(source: String): Array[Boolean] = {
    val mask = Array.fill(source.length)(true)
    var i = 0
    var blockDepth = 0
    var inLineComment = false
    var inString = false
    var inTripleString = false
    var inChar = false
    var escaped = false

    while (i < source.length) {
      val ch = source.charAt(i)
      val next = if (i + 1 < source.length) source.charAt(i + 1) else 0.toChar
      val next2 = if (i + 2 < source.length) source.charAt(i + 2) else 0.toChar

      if (inLineComment) {
        mask(i) = false
        if (ch == '\n') {
          inLineComment = false
          mask(i) = true
        }
      } else if (blockDepth > 0) {
        mask(i) = false
        if (ch == '/' && next == '*') {
          mask(i + 1) = false
          blockDepth += 1
          i += 1
        } else if (ch == '*' && next == '/') {
          mask(i + 1) = false
          blockDepth -= 1
          i += 1
        }
      } else if (inTripleString) {
        mask(i) = false
        if (ch == '"' && next == '"' && next2 == '"') {
          mask(i + 1) = false
          mask(i + 2) = false
          inTripleString = false
          i += 2
        }
      } else if (inString) {
        mask(i) = false
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '"') {
          inString = false
        }
      } else if (inChar) {
        mask(i) = false
        if (escaped) {
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '\'') {
          inChar = false
        }
      } else if (ch == '/' && next == '/') {
        mask(i) = false
        mask(i + 1) = false
        inLineComment = true
        i += 1
      } else if (ch == '/' && next == '*') {
        mask(i) = false
        mask(i + 1) = false
        blockDepth = 1
        i += 1
      } else if (ch == '"' && next == '"' && next2 == '"') {
        mask(i) = false
        mask(i + 1) = false
        mask(i + 2) = false
        inTripleString = true
        i += 2
      } else if (ch == '"') {
        mask(i) = false
        inString = true
      } else if (ch == '\'') {
        mask(i) = false
        inChar = true
      }
      i += 1
    }
    mask
  }

  private def startsMember(source: String, code: Array[Boolean], index: Int): Boolean = {
    Seq("override", "private", "protected", "def", "val", "lazy", "object", "class", "trait")
      .exists(token => tokenAt(source, code, index, token))
  }

  private def tokenAt(source: String, code: Array[Boolean], index: Int, token: String): Boolean = {
    index >= 0 &&
    index + token.length <= source.length &&
    source.substring(index, index + token.length) == token &&
    token.indices.forall(offset => code(index + offset)) &&
    (index == 0 || !isIdentifierPart(source.charAt(index - 1))) &&
    (index + token.length == source.length || !isIdentifierPart(source.charAt(index + token.length)))
  }

  private def readIdentifier(
      source: String,
      code: Array[Boolean],
      start: Int
  ): Option[(String, Int)] = {
    if (start < source.length && source.charAt(start) == '`' && code(start)) {
      var i = start + 1
      while (i < source.length && !(source.charAt(i) == '`' && code(i))) {
        i += 1
      }
      Option.when(i < source.length)(source.substring(start + 1, i) -> (i + 1))
    } else {
      var i = start
      while (i < source.length && code(i) && isIdentifierPart(source.charAt(i))) {
        i += 1
      }
      Option.when(i > start)(source.substring(start, i) -> i)
    }
  }

  private def skipWhitespace(source: String, code: Array[Boolean], start: Int): Int = {
    var i = start
    while (i < source.length && (!code(i) || source.charAt(i).isWhitespace)) {
      i += 1
    }
    i
  }

  private def nextNonWhitespace(source: String, start: Int): Option[Int] = {
    var i = start
    while (i < source.length && source.charAt(i).isWhitespace) {
      i += 1
    }
    Option.when(i < source.length)(i)
  }

  private def codeSlice(source: String, code: Array[Boolean], start: Int, end: Int): String = {
    val builder = new StringBuilder
    var i = start
    while (i < end) {
      if (code(i)) {
        builder.append(source.charAt(i))
      }
      i += 1
    }
    builder.result()
  }

  private def normalizeExpression(value: String): String = {
    value.filterNot(_.isWhitespace)
  }

  private def lineStart(source: String, lineNum: Int): Option[Int] = {
    if (lineNum <= 0) {
      None
    } else if (lineNum == 1) {
      Some(0)
    } else {
      var currentLine = 1
      var i = 0
      var found: Option[Int] = None
      while (i < source.length && found.isEmpty) {
        if (source.charAt(i) == '\n') {
          currentLine += 1
          if (currentLine == lineNum) {
            found = Some(i + 1)
          }
        }
        i += 1
      }
      found
    }
  }

  private def lineStartAt(source: String, index: Int): Int = {
    var i = index.min(source.length - 1).max(0)
    while (i > 0 && source.charAt(i - 1) != '\n') {
      i -= 1
    }
    i
  }

  private def lineEnd(source: String, start: Int): Int = {
    val idx = source.indexOf('\n', start)
    if (idx == -1) {
      source.length
    } else {
      idx
    }
  }

  private def lineIndent(source: String, index: Int): String = {
    val start = lineStartAt(source, index)
    source.substring(start, lineEnd(source, start)).takeWhile(ch => ch == ' ' || ch == '\t')
  }

  private def inferredItemIndent(
      source: String,
      start: Int,
      end: Int,
      fallbackCloseIndent: String
  ): String = {
    val inside = source.substring(start, end)
    inside.linesIterator.drop(1).find(_.trim.nonEmpty).map(_.takeWhile(ch => ch == ' ' || ch == '\t'))
      .getOrElse(fallbackCloseIndent + "  ")
  }

  private def isIdentifierPart(ch: Char): Boolean = {
    ch.isLetterOrDigit || ch == '_' || ch == '$'
  }
}
