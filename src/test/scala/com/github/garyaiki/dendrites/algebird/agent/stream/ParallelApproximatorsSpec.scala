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
package com.github.garyaiki.dendrites.algebird.agent.stream

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.twitter.algebird.{QTree, QTreeSemigroup}
import com.twitter.algebird.CMSHasherImplicits._
import com.twitter.algebird.{AveragedValue, CMS, HLL, QTree}
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.Matchers._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures.whenReady
import org.scalatest.time.SpanSugar._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import com.github.garyaiki.dendrites.aggregator.mean
import com.github.garyaiki.dendrites.algebird.AlgebirdConfigurer
import com.github.garyaiki.dendrites.algebird.{cmsHasherBigDecimal, cmsHasherDouble, cmsHasherFloat}
import com.github.garyaiki.dendrites.algebird.agent.Agents
import com.github.garyaiki.dendrites.algebird.typeclasses.QTreeLike
import com.github.garyaiki.dendrites.fixtures.TestValuesBuilder

/**
  * @author Gary Struthers
  *
  */
class ParallelApproximatorsSpec extends WordSpecLike with TestValuesBuilder {
  implicit val system = ActorSystem("dendrites")
  implicit val materializer = ActorMaterializer()
  implicit val logger = Logging(system, getClass)
  val halfLife = AlgebirdConfigurer.decayedValueHalfLife
  implicit val m = AlgebirdConfigurer.decayedValueMonoid
  implicit val ag = AlgebirdConfigurer.hyperLogLogAgggregator
  implicit val monoid = AlgebirdConfigurer.hyperLogLogMonoid
  val qTreeLevel = AlgebirdConfigurer.qTreeLevel
  val timeout = Timeout(3000 millis)

  "A BigDecimals ParallelApproximators" should {
    "update all agents and return their latest values" in {
      implicit val qtBDSemigroup = new QTreeSemigroup[BigDecimal](qTreeLevel)
      val agents = new Agents[BigDecimal]("test BigDecimal approximators agents")
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val composite = ParallelApproximators.compositeFlow[BigDecimal](avgAgent,
        cmsAgent,
        dvAgent,
        hllAgent,
        qtAgent,
        DecayedValueAgentFlow.nowMillis)

      val ffg = Flow.fromGraph(composite)
      val (pub, sub) = TestSource.probe[Seq[BigDecimal]]
        .via(ffg)
        .toMat(TestSink.probe[(Future[AveragedValue],
            Future[CMS[BigDecimal]],
            Future[Seq[com.twitter.algebird.DecayedValue]],
            Future[HLL],
            Future[QTree[BigDecimal]])])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(bigDecimals)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        result.count should equal(bigDecimals.size)
        val mD = mean(bigDecimals)
        val expectMean = mD.right.get.toDouble
        result.value shouldBe expectMean  +- 0.1
      }

      val updateCMSFuture = cmsAgent.agent.future()
      whenReady(updateCMSFuture, timeout) { result => result.totalCount shouldBe longs.size }

      val updateDVFuture = dvAgent.agent.future()
      whenReady(updateDVFuture, timeout) {  result => result.size shouldBe bigDecimals.size +- 2 }

      val updateHLLFuture = hllAgent.agent.future()
      whenReady(updateHLLFuture, timeout) { result =>
        result.estimatedSize shouldBe longs.distinct.size.toDouble +- 0.09
      }

      val updateQTFuture = qtAgent.agent.future()
      whenReady(updateQTFuture, timeout) { result => result.count shouldBe bigDecimals.size }
    }
  }

  "A BigInt ParallelApproximators" should {
    "update all agents and return their latest values" in {
      implicit val qtBDSemigroup = new QTreeSemigroup[BigInt](qTreeLevel)

      val agents = new Agents[BigInt]("test BigInt approximators agents")
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val composite = ParallelApproximators.compositeFlow[BigInt](avgAgent,
        cmsAgent,
        dvAgent,
        hllAgent,
        qtAgent,
        DecayedValueAgentFlow.nowMillis)
      val ffg = Flow.fromGraph(composite)
      val (pub, sub) = TestSource.probe[Seq[BigInt]]
        .via(ffg)
        .toMat(TestSink.probe[(Future[AveragedValue],
            Future[CMS[BigInt]],
            Future[Seq[com.twitter.algebird.DecayedValue]],
            Future[HLL],
            Future[QTree[BigInt]])])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(bigInts)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        result.count shouldBe bigInts.size
        val mD = mean(bigInts)
        val expectMean = mD.right.get.toDouble
        result.value shouldBe expectMean  +- 0.5
      }

      val updateCMSFuture = cmsAgent.agent.future()
      whenReady(updateCMSFuture, timeout) { result => result.totalCount shouldBe longs.size }

      val updateDVFuture = dvAgent.agent.future()
      whenReady(updateDVFuture, timeout) {  result => result.size shouldBe bigInts.size +- 2 }

      val updateHLLFuture = hllAgent.agent.future()
      whenReady(updateHLLFuture, timeout) { result =>
        result.estimatedSize shouldBe longs.distinct.size.toDouble +- 0.09
      }

      val updateQTFuture = qtAgent.agent.future()
      whenReady(updateQTFuture, timeout) { result => result.count shouldBe bigInts.size }
    }
  }

  "A Double ParallelApproximators" should {
    "update all agents and return their latest values" in {
      implicit val qtBDSemigroup = new QTreeSemigroup[Double](qTreeLevel)

      val agents = new Agents[Double]("test Double approximators agents")
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val composite = ParallelApproximators.compositeFlow[Double](avgAgent,
        cmsAgent,
        dvAgent,
        hllAgent,
        qtAgent,
        DecayedValueAgentFlow.nowMillis)
      val ffg = Flow.fromGraph(composite)
      val (pub, sub) = TestSource.probe[Seq[Double]]
        .via(ffg)
        .toMat(TestSink.probe[(Future[AveragedValue],
            Future[CMS[Double]],
            Future[Seq[com.twitter.algebird.DecayedValue]],
            Future[HLL],
            Future[QTree[Double]])])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(doubles)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        result.count shouldBe doubles.size
        val mD = mean(doubles)
        val expectMean = mD.right.get
        result.value shouldBe expectMean  +- 0.5
      }

      val updateCMSFuture = cmsAgent.agent.future()
      whenReady(updateCMSFuture, timeout) { result => result.totalCount shouldBe longs.size }

      val updateDVFuture = dvAgent.agent.future()
      whenReady(updateDVFuture, timeout) { result => result.size shouldBe bigInts.size +- 2 }

      val updateHLLFuture = hllAgent.agent.future() // @FIXME
      whenReady(updateHLLFuture, timeout) { result => result.estimatedSize shouldBe 3.0 +- 0.09 }

      val updateQTFuture = qtAgent.agent.future()
      whenReady(updateQTFuture, timeout) { result => result.count shouldBe doubles.size }
    }
  }

  "A Float ParallelApproximators" should {
    "update all agents and return their latest values" in {
      implicit val qtBDSemigroup = new QTreeSemigroup[Float](qTreeLevel)

      val agents = new Agents[Float]("test Float approximators agents")
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val composite = ParallelApproximators.compositeFlow[Float](avgAgent,
        cmsAgent,
        dvAgent,
        hllAgent,
        qtAgent,
        DecayedValueAgentFlow.nowMillis)
      val ffg = Flow.fromGraph(composite)
      val (pub, sub) = TestSource.probe[Seq[Float]]
        .via(ffg)
        .toMat(TestSink.probe[(Future[AveragedValue],
            Future[CMS[Float]],
            Future[Seq[com.twitter.algebird.DecayedValue]],
            Future[HLL],
            Future[QTree[Float]])])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(floats)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        result.count shouldBe ints.size
        val mD = mean(ints)
        val expectMean = mD.right.get.toDouble
        result.value shouldBe expectMean  +- 12.0
      }

      val updateCMSFuture = cmsAgent.agent.future()
      whenReady(updateCMSFuture, timeout) { result => result.totalCount shouldBe longs.size }

      val updateDVFuture = dvAgent.agent.future()
      whenReady(updateDVFuture, timeout) { result => result.size shouldBe ints.size +- 2 }

      val updateHLLFuture = hllAgent.agent.future()
      whenReady(updateHLLFuture, timeout) { result =>
        result.estimatedSize shouldBe longs.distinct.size.toDouble +- 0.09
      }

      val updateQTFuture = qtAgent.agent.future()
      whenReady(updateQTFuture, timeout) { result => result.count shouldBe floats.size }
    }
  }

  "A Int ParallelApproximators" should {
    "update all agents and return their latest values" in {
      implicit val qtBDSemigroup = new QTreeSemigroup[Int](qTreeLevel)

      val agents = new Agents[Int]("test Int approximators agents")
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val composite = ParallelApproximators.compositeFlow[Int](avgAgent,
        cmsAgent,
        dvAgent,
        hllAgent,
        qtAgent,
        DecayedValueAgentFlow.nowMillis)
      val ffg = Flow.fromGraph(composite)
      val (pub, sub) = TestSource.probe[Seq[Int]]
        .via(ffg)
        .toMat(TestSink.probe[(Future[AveragedValue],
            Future[CMS[Int]],
            Future[Seq[com.twitter.algebird.DecayedValue]],
            Future[HLL],
            Future[QTree[Int]])])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(ints)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        result.count shouldBe ints.size
        val mD = mean(ints)
        val expectMean = mD.right.get.toDouble
        result.value shouldBe expectMean  +- 0.5
      }

      val updateCMSFuture = cmsAgent.agent.future()
      whenReady(updateCMSFuture, timeout) { result => result.totalCount shouldBe longs.size }

      val updateDVFuture = dvAgent.agent.future()
      whenReady(updateDVFuture, timeout) { result => result.size shouldBe floats.size +- 2 }

      val updateHLLFuture = hllAgent.agent.future()
      whenReady(updateHLLFuture, timeout) { result =>
        result.estimatedSize shouldBe longs.distinct.size.toDouble +- 0.09
      }

      val updateQTFuture = qtAgent.agent.future()
      whenReady(updateQTFuture, timeout) { result => result.count shouldBe floats.size }
    }
  }

  "A Long ParallelApproximators" should {
    "update all agents and return their latest values" in {
      implicit val qtBDSemigroup = new QTreeSemigroup[Long](qTreeLevel)

      val agents = new Agents[Long]("test Long approximators agents")
      val avgAgent = agents.avgAgent
      val cmsAgent = agents.cmsAgent
      val dvAgent = agents.dcaAgent
      val hllAgent = agents.hllAgent
      val qtAgent = agents.qtAgent
      val composite = ParallelApproximators.compositeFlow[Long](avgAgent,
        cmsAgent,
        dvAgent,
        hllAgent,
        qtAgent,
        DecayedValueAgentFlow.nowMillis)
      val ffg = Flow.fromGraph(composite)
      val (pub, sub) = TestSource.probe[Seq[Long]]
        .via(ffg)
        .toMat(TestSink.probe[(Future[AveragedValue],
            Future[CMS[Long]],
            Future[Seq[com.twitter.algebird.DecayedValue]],
            Future[HLL],
            Future[QTree[Long]])])(Keep.both)
        .run()
      sub.request(1)
      pub.sendNext(longs)
      val response = sub.expectNext()
      pub.sendComplete()
      sub.expectComplete()

      val updateAvgFuture = avgAgent.agent.future()
      whenReady(updateAvgFuture, timeout) { result =>
        result.count shouldBe longs.size
        val mD = mean(longs)
        val expectMean = mD.right.get.toDouble
        result.value shouldBe expectMean  +- 0.5
      }

      val updateCMSFuture = cmsAgent.agent.future()
      whenReady(updateCMSFuture, timeout) { result => result.totalCount shouldBe longs.size }

      val updateDVFuture = dvAgent.agent.future()
      whenReady(updateDVFuture, timeout) { result => result.size shouldBe longs.size +- 2 }

      val updateHLLFuture = hllAgent.agent.future()
      whenReady(updateHLLFuture, timeout) { result =>
        result.estimatedSize shouldBe longs.distinct.size.toDouble +- 0.09
      }

      val updateQTFuture = qtAgent.agent.future()
      whenReady(updateQTFuture, timeout) { result => result.count shouldBe longs.size }
    }
  }
}
