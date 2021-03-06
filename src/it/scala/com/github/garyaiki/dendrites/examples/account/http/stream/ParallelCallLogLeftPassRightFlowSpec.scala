/**
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.github.garyaiki.dendrites.examples.account.http.stream

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures.whenReady
import org.scalatest.time.SpanSugar._
import scala.concurrent.ExecutionContext
import scala.math.BigDecimal.double2bigDecimal
import com.github.garyaiki.dendrites.examples.account.{CheckingAccountBalances, GetAccountBalances,
  MoneyMarketAccountBalances, SavingsAccountBalances}
import com.github.garyaiki.dendrites.examples.account.http.{BalancesProtocols, CheckingBalancesClientConfig}
import com.github.garyaiki.dendrites.http.{caseClassToGetQuery, typedQueryResponse}

/**
  *
  * @author Gary Struthers
  */
class ParallelCallLogLeftPassRightFlowSpec extends WordSpecLike with Matchers with BeforeAndAfter
    with BalancesProtocols {
  implicit val system = ActorSystem("dendrites")
  implicit val ec: ExecutionContext = system.dispatcher
  override implicit val mat = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  val timeout = Timeout(3000 millis)

  def source = TestSource.probe[Product]
  def sinkRight = TestSink.probe[Seq[AnyRef]]
  val pcf = new ParallelCallFlow
  val wrappedFlow = pcf.wrappedCallsLogLeftPassRightFlow

  before { // init connection pool
    val id = 1L
    val clientConfig = new CheckingBalancesClientConfig()
    val baseURL = clientConfig.baseURL

    def partial = typedQueryResponse(baseURL, "GetAccountBalances", caseClassToGetQuery, mapPlain, mapChecking) _
    val responseFuture = partial(GetAccountBalances(id))

    whenReady(responseFuture, Timeout(120000 millis)) { result => }
  }

  "A ParallelCallLeftRightFlowClient" should {
    "get balances for id 1" in {
      val id = 1L
      val (pub, sub) = source.via(wrappedFlow).toMat(sinkRight)(Keep.both).run()
      sub.request(1)
      pub.sendNext(GetAccountBalances(id))
      Thread.sleep(40000)
      val response: Seq[AnyRef] = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      response.length should equal(3)
    }
  }
}
