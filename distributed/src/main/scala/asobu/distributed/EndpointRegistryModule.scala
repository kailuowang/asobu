package asobu.distributed

import play.api.inject._
import play.api.{Configuration, Environment}

class EndpointRegistryModule extends Module {

  def bindings(
    environment: Environment,
    configuration: Configuration
  ) = Seq(
    bind[EndpointRegistryProvider].toSelf.eagerly
  )
}
