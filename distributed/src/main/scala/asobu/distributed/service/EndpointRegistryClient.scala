package asobu.distributed.service

import scala.concurrent.Future
import asobu.distributed._

trait EndpointRegistryClient {
  def add(endpointDefinition: EndpointDefinition): Future[Unit]
}
