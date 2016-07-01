package org.gs.http.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Flow}
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import java.util.concurrent.Executors
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatest._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.SpanSugar._
import scala.math.BigDecimal.double2bigDecimal
import org.gs.examples.account.{ CheckingAccountBalances, GetAccountBalances}
import org.gs.examples.account.http.{BalancesProtocols, CheckingBalancesClientConfig}
import org.gs.http.{caseClassToGetQuery, typedQueryResponse}

/**
  *
  * @author Gary Struthers
  */
class TypedQueryResponseFlowSpec extends WordSpecLike with Matchers with BalancesProtocols {
  implicit val system = ActorSystem("dendrites")
  override implicit val materializer = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  implicit val executor = Executors.newSingleThreadExecutor()
  val timeout = Timeout(3000 millis)

  def source = TestSource.probe[Product]
  def sink = TestSink.probe[Either[String, AnyRef]]

  val clientConfig = new CheckingBalancesClientConfig()
  val hostConfig = clientConfig.hostConfig
  val baseURL = clientConfig.baseURL
  val requestPath = clientConfig.requestPath
  val queryFlow = new TypedQueryFlow(baseURL, requestPath, caseClassToGetQuery)
  val responseFlow = new TypedResponseFlow(mapPlain, mapChecking)
  val tqr = new TypedQueryResponseFlow(queryFlow, responseFlow)
  val testFlow = source.via(tqr.flow).toMat(sink)(Keep.both)

  "A TypedQueryResponseFlow for Checking balances" should {
    "get balances for id 1" in {
      val id = 1L
      val (pub, sub) = testFlow.run()
      sub.request(1)
      pub.sendNext(GetAccountBalances(id))
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      response should equal(Right(CheckingAccountBalances[BigDecimal](Some(List((1, 1000.1))))))
    }
  }

  it should {
    "get balances for id 2" in {
      val id = 2L
      val (pub, sub) = testFlow.run()
      sub.request(1)
      pub.sendNext(GetAccountBalances(id))
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()
      
      response should equal(Right(CheckingAccountBalances(Some(List((2L, BigDecimal(2000.20)),
          (22L, BigDecimal(2200.22)))))))
    }
  }

  it should {
    "get balances for id 3" in {
      val id = 3L
      val (pub, sub) = testFlow.run()
      sub.request(1)
      pub.sendNext(GetAccountBalances(id))
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()
      
      response should equal(Right(CheckingAccountBalances(Some(List((3L, BigDecimal(3000.30)),
          (33L, BigDecimal(3300.33)),
          (333L, BigDecimal(3330.33)))))))
    }
  }

  it should {
    "not find bad ids" in {
      val id = 4L
      val (pub, sub) = testFlow.run()
      sub.request(1)
      pub.sendNext(GetAccountBalances(id))
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()
      
      response should equal(Left("Checking account 4 not found"))
    }
  }
  
  val badBaseURL = clientConfig.baseURL.dropRight(1)

  def badPartial = typedQueryResponse(
          badBaseURL, "GetAccountBalances", caseClassToGetQuery, mapPlain, mapChecking) _

  def badFlow: Flow[Product, Either[String, AnyRef], NotUsed] = Flow[Product].mapAsync(1)(badPartial)

  it should {
    "fail bad request URLs" in {
      val id = 1L
      val (pub, sub) = source
        .via(badFlow)
        .toMat(sink)(Keep.both).run()
      sub.request(1)
      pub.sendNext(GetAccountBalances(id))
      val response = sub.expectNext()
      response should equal(Left("FAIL 404 Not Found The requested resource could not be found."))
    }
  }
}
