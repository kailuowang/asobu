package asobu.distributed

trait EndpointRegistry {

}

object EndpointRegistry {
  case class Add(endpointDefs: List[EndpointDefinition])
}
