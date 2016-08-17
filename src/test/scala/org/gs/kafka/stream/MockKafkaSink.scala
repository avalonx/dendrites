/** Copyright 2016 Gary Struthers

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
package org.gs.kafka.stream

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.{ActorAttributes, Attributes, Inlet, SinkShape, Supervision}
import akka.stream.ActorAttributes.SupervisionStrategy
import akka.stream.scaladsl.Sink
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic, InHandler}
import org.apache.kafka.clients.producer.{Callback, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.{CorruptRecordException, // Retriable exceptions
  InvalidMetadataException,
  NotEnoughReplicasAfterAppendException,
  NotEnoughReplicasException,
  OffsetOutOfRangeException,
  TimeoutException,
  UnknownTopicOrPartitionException,
  RetriableException}
import org.apache.kafka.common.errors.{InvalidTopicException, //Stopping exceptions
  OffsetMetadataTooLarge,
  RecordBatchTooLargeException,
  RecordTooLargeException,
  UnknownServerException}
import scala.util.control.NonFatal
import org.gs.kafka.ProducerConfig
import org.gs.kafka.stream.KafkaSink.decider

/** A copy of [[org.gs.kafka.stream.KafkaSink]] modified to inject exceptions into Kafka Producer
  * asynchronous callback
  *
  * @tparam K Kafka ProducerRecord key
  * @tparam V Type of serialized object received from stream and Kafka ProducerRecord value
  * @param wProd extends KafkaProducer with key, value, and topic fields
  * @param testException for injecting exceptions to test Supervision
  * @param implicit logger
  *
  * @author Gary Struthers
  *
  */
class MockKafkaSink[K, V](wProd: ProducerConfig[K, V], testException: RuntimeException = null)
        (implicit logger: LoggingAdapter) extends GraphStage[SinkShape[V]] {

  var handledTestException = false
  val producer = wProd.producer
  /* for access to MockProducer debugging methods
  import org.apache.kafka.clients.producer.MockProducer
  val mockProducer = producer match {
    case x: MockProducer[K, V] => x
  }*/
  val in = Inlet[V](s"MockKafkaSink")
  override val shape: SinkShape[V] = SinkShape(in)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {

      private def decider = inheritedAttributes.get[SupervisionStrategy].map(_.decider).
          getOrElse(Supervision.stoppingDecider)

      /** pull initializes stream requests */
      override def preStart(): Unit = {
        pull(in)
      }

      /** exception handler for producer send's callback */
      def asyncExceptions(pRecord: ProducerRecord[K, V], callback: Callback)(e: Exception): Unit = {
        e match {
          case NonFatal(e) => decider(e) match {
              case Supervision.Stop => {
                failStage(e)
              }
              case Supervision.Resume => {
                logger debug("Kafka send retryable exception, attempt retry {}", e.getMessage)
                producer send(pRecord, callback)
              }
          }
        }
      }

      /** send ProducerRecord to Kafka with callback, on success pull from upstream, on failure
        * retry on RetryableException, log and throw other exceptions
        */
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val item = grab(in)
          val producerRecord = new ProducerRecord[K, V](wProd.topic, wProd.key, item)
          var errorCallback: AsyncCallback[Exception] = null
          val pullCallback = getAsyncCallback{ (_: Unit) => pull(in) }
          val kafkaCallback = new Callback() {
            def onCompletion(meta: RecordMetadata, e: Exception): Unit = {
              if(!handledTestException && testException != null) {
                handledTestException = true
                errorCallback invoke(testException)
              }
              else pullCallback invoke((): Unit)
            }
          }
          val curriedAsyncEx = asyncExceptions(producerRecord, kafkaCallback) _
          errorCallback = getAsyncCallback(curriedAsyncEx)
          producer send(producerRecord, kafkaCallback)
        }
      })
    }
  }
}

/** Factory for KafkaSink with wrapped KafkaProducer and its properties configuration
  * Sink.fromGraph promotes KafkaSink from a SinkShape to a Sink
  */
object MockKafkaSink {

  /** Create MockKafkaSink as Akka Sink with KafkaSink Supervision
    *
    * @tparam K key type
    * @tparam V value type
  	* @param producer configuration object
 		* @param implicit logger
  	* @return Sink[V, NotUsed]
  	*/
  def apply[K, V](producer: ProducerConfig[K, V], testException: RuntimeException)
          (implicit logger: LoggingAdapter): Sink[V, NotUsed] = {
    val sink = Sink.fromGraph(new MockKafkaSink[K, V](producer, testException))
    sink.withAttributes(ActorAttributes.supervisionStrategy(decider))
  }
}