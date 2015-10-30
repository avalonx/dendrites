package org.gs.algebird.agent

import org.gs.algebird._
import org.gs.fixtures.TestValuesBuilder
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatest._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.SpanSugar._
import scala.concurrent.ExecutionContext.Implicits.global

class CountMinSketchAgentSpec extends WordSpecLike with Matchers with TestValuesBuilder {

  val timeout = Timeout(3000 millis)

  "A CountMinSketchAgent totalCount" should {
    "equal total size" in {
      val aa = new CountMinSketchAgent("test Longs")
      val updateFuture = aa.update(longs)
      whenReady(updateFuture, timeout) { result =>
        result.totalCount should equal(longs.size)
      }
    }
  }

}