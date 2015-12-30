package asobu.distributed

import play.api.test.{FakeRequest, PlaySpecification}
import play.core.routing.RouteParams

import play.routes.compiler._

object EndpointSpec extends PlaySpecification {
  import play.api.http.HttpVerbs._

  val routeString =
    """
      |# Some Comments
      |# Some other Comments
      |GET   /ep1/a/:n   a.b.c(n: Int)
      |
      |GET   /ep2/:a/something   a.b.d(a: Int, b: String)
      |
    """.stripMargin

  lazy val parserResult = Endpoint.parse(EndpointDefs(routeString))
  lazy val endPoints = parserResult.right.get
  lazy val ep1: Endpoint = endPoints(0)
  lazy val ep2: Endpoint = endPoints(1)

  "Parse to endpoints" >> {
    parserResult must beRight[List[Endpoint]]
    parserResult.right.get.length === 2

    "parse comments" >> {
      val route: Route = ep1.routeInfo
      route.verb === HttpVerb(GET)
      route.comments === List(Comment(" Some Comments"), Comment(" Some other Comments"))
    }

    "parse params" >> {
      val route: Route = ep1.routeInfo
      val parameter = route.call.parameters.get.head
      parameter.name === "n"
      parameter.typeName === "Int"
    }

  }

  "find Handler with pathParameters" >> {
    val expectedParams = RouteParams(Map("n" → Right("3")), Map())
    ep1.matchHandler(FakeRequest(GET, "/ep1/a/3")) must beSome(EndpointHandler(expectedParams, ep1))
  }

  "find Handler with queryParameters" >> {
    val expectedParams = RouteParams(Map("a" → Right("3")), Map("b" → Seq("foo")))
    ep2.matchHandler(FakeRequest(GET, "/ep2/3/something?b=foo")) must beSome(EndpointHandler(expectedParams, ep2))
  }

  "does not find Handler for unmatched method" >> {
    ep2.matchHandler(FakeRequest(POST, "/ep2/3/something?b=foo")) must beNone
  }
  "does not find Handler for unmached path" >> {
    ep2.matchHandler(FakeRequest(GET, "/epnotfound")) must beNone
  }

}

