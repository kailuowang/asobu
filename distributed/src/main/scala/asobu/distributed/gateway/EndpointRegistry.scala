package asobu.distributed.gateway

import asobu.distributed.EndpointDefinition

trait EndpointRegistry {

}

object EndpointRegistry {
  case class Add(endpointDefs: List[EndpointDefinition])
}
