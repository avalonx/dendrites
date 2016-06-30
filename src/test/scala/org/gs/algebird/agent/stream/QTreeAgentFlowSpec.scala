/**
  */
package org.gs.algebird.agent.stream

import akka.actor.ActorSystem
import akka.event.{ LoggingAdapter, Logging }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Keep }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import com.twitter.algebird.{QTree, QTreeSemigroup}
import org.scalatest.WordSpecLike
import org.scalatest.Matchers._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.SpanSugar._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.gs.aggregator.mean
import org.gs.algebird.{AlgebirdConfigurer, BigDecimalField}
import org.gs.algebird.agent.QTreeAgent
//import org.gs.algebird.stream.avgFlow
import org.gs.algebird.typeclasses.QTreeLike
import org.gs.fixtures.TestValuesBuilder

/**
  * @author Gary Struthers
  *
  */
class QTreeAgentFlowSpec extends WordSpecLike with TestValuesBuilder {
  implicit val system = ActorSystem("dendrites")
  implicit val materializer = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  val level = AlgebirdConfigurer.qTreeLevel
  implicit val qtBDSemigroup = new QTreeSemigroup[BigDecimal](level)
  val timeout = Timeout(3000 millis)

  "A QTreeAgentFlow of BigDecimals" should {
	  val qtAgt = new QTreeAgent[BigDecimal]("test BigDecimals")
			  val qtAgtFlow = new QTreeAgentFlow(qtAgt)
	  val (pub, sub) = TestSource.probe[Seq[BigDecimal]]
			  .via(qtAgtFlow)
			  .toMat(TestSink.probe[Future[QTree[BigDecimal]]])(Keep.both)
			  .run()
			  sub.request(1)
			  pub.sendNext(bigDecimals)
			  val updateFuture = sub.expectNext()
			  pub.sendComplete()
			  sub.expectComplete()
			  "update its count" in {
		  whenReady(updateFuture, timeout) { result =>
		  result.count should equal(bigDecimals.size)
		  }
	  }
	  "update its lower bound" in {
		  whenReady(updateFuture, timeout) { result =>
		  result.lowerBound <= bigDecimals.min
		  }
	  }
	  "update its upper bound" in {
		  whenReady(updateFuture, timeout) { result =>
		  result.upperBound >= bigDecimals.max
		  }
	  }
	  "update its 1st quantile bound" in {
		  whenReady(updateFuture, timeout) { result =>
		  val fstQB = result.quantileBounds(0.25)
		  val q1 = 103.0
		  fstQB._1 >= q1
		  fstQB._2 <= q1 + 0.0001
		  }
	  }
	  "update its 2nd quantile bound" in {
		  whenReady(updateFuture, timeout) { result =>
		  val sndQB = result.quantileBounds(0.5)
		  val q2 = 110.0
		  sndQB._1 >= q2
		  sndQB._2 <= q2 + 0.0001
		  }
	  }     
	  "update its 3rd quantile bound" in {
		  whenReady(updateFuture, timeout) { result =>
		  val trdQB = result.quantileBounds(0.75)
		  val q3 = 116.0
		  trdQB._1 >= q3
		  trdQB._2 <= q3 + 0.0001
		  }
	  }
	  "update its range sum bounds" in {
		  whenReady(updateFuture, timeout) { result =>
		  val rsb = result.rangeSumBounds(result.lowerBound, result.upperBound)
		  rsb should equal (bigDecimals.sum, bigDecimals.sum)
		  }
	  }
	  "update its range count bounds" in {
		  whenReady(updateFuture, timeout) { result =>
		  val rcb = result.rangeCountBounds(result.lowerBound, result.upperBound)
		  rcb should equal (bigDecimals.size, bigDecimals.size)
		  }
	  }
  }
}