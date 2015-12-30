package asobu.distributed

/**
 * the yet to release routes compiler code
 */
object RoutesCompilerExtra {
  import play.routes.compiler.templates._
  /**
   * Encode the given String constant as a triple quoted String.
   *
   * This will split the String at any $ characters, and use concatenation to concatenate a single $ String followed
   * be the remainder, this is to avoid "possible missing interpolator" false positive warnings.
   *
   * That is to say:
   *
   * {{{
   * /foo/$id<[^/]+>
   * }}}
   *
   * Will be encoded as:
   *
   * {{{
   *   """/foo/""" + "$" + """id<[^/]+>"""
   * }}}
   */
  def encodeStringConstant(constant: String) = {
    constant.split('$').mkString(tq, s"""$tq + "$$" + $tq""", tq)
  }
}
