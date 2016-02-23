package asobu

import asobu.dsl.ExtractResult
import cats.data.Kleisli
import play.core.routing.RouteParams

package object distributed {
  type RouteParamsExtractor[T] = Kleisli[ExtractResult, RouteParams, T]
}
