package asobu.distributed

import scala.concurrent.Future

trait EndpointRegistryClient {
  def add(endpointDefinition: EndpointDefinition): Future[Unit]
}
