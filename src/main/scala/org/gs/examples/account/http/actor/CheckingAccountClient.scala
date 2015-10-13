package org.gs.examples.account.http.actor

import akka.actor.{ Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, Props }
import akka.contrib.pattern.Aggregator
import akka.event.{ LoggingAdapter, Logging }
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.typesafe.config.Config
import org.gs.akka.aggregator.ResultAggregator
import org.gs.examples.account.{ Checking, CheckingAccountBalances, GetAccountBalances }
import org.gs.examples.account.http.{ BalancesProtocols, CheckingBalancesClientConfig }
import org.gs.http._

class CheckingAccountClient extends Actor with BalancesProtocols with ActorLogging {
  import context._
  override implicit val system = context.system
  override implicit val materializer = ActorMaterializer()
  implicit val logger = log
  val clientConfig = new CheckingBalancesClientConfig()
  val hostConfig = clientConfig.hostConfig
  val config = hostConfig._1
/* for debugging
  override def preStart() = {
    log.debug(s"Starting ${this.toString()}")
  }
*/
  override def preRestart(reason: Throwable, message: Option[Any]) {
    log.error(reason, "Restarting due to [{}] when processing [{}]",
      reason.getMessage, message.getOrElse(""))
  }

  def receive = {
    case GetAccountBalances(id: Long) ⇒ {
      val callFuture = HigherOrderCalls.call(GetAccountBalances(id), clientConfig.baseURL)
      val responseFuture = HigherOrderCalls.byId(id, callFuture, mapChecking, mapPlain)
      responseFuture pipeTo sender
    }
  }
}

object CheckingAccountClient {
  def props = Props[CheckingAccountClient]
}

trait CheckingAccountCaller {
  this: Actor with ResultAggregator with Aggregator ⇒

  def fetchCheckingAccountsBalance(context: ActorContext, id: Long, recipient: ActorRef) {
    context.actorOf(CheckingAccountClient.props) ! GetAccountBalances(id)
    expectOnce {
      case CheckingAccountBalances(balances) ⇒
        addResult(0, (Checking -> balances), recipient)
    }
  }
}