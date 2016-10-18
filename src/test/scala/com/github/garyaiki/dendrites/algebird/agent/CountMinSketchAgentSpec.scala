package org.gs.algebird.agent

import com.twitter.algebird.CMSHasherImplicits._
import org.scalatest.{ Matchers, WordSpecLike }
import org.scalatest._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.SpanSugar._
import scala.concurrent.ExecutionContext.Implicits.global
import org.gs.algebird.{createCMSMonoid, createCountMinSketch} 
import org.gs.fixtures.TestValuesBuilder

class CountMinSketchAgentSpec extends WordSpecLike with Matchers with TestValuesBuilder {
  implicit val m = createCMSMonoid[Long]()
  val timeout = Timeout(3000 millis)

  "A CountMinSketchAgent totalCount" should {
    "equal total size" in {
      val cmsAgt = new CountMinSketchAgent[Long]("test Longs")
      val cms0 = createCountMinSketch(longs)
      val updateFuture = cmsAgt.alter(cms0)
      whenReady(updateFuture, timeout) { result =>
        result.totalCount should equal(longs.size)
      }
    }
  }

}