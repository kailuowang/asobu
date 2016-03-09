package asobu.distributed

import scala.util.control.{NoStackTrace, ControlThrowable}

case class RoutesParsingException(msg: String) extends Exception(msg)
