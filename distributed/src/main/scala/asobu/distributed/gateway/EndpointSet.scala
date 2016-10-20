package asobu.distributed.gateway

import asobu.distributed.protocol._

case class EndpointSet(
  prefix: Prefix,
  endpoints: Set[Endpoint],
  handlerHosts: Set[HandlerHost],
  version: Version
)
