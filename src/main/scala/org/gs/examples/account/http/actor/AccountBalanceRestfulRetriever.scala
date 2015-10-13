package org.gs.examples.account.http.actor

import akka.actor._
import akka.contrib.pattern.Aggregator
import org.gs.akka.aggregator.{ CantUnderstand, ResultAggregator, TimedOut }
import org.gs.examples.account._
import org.gs.examples.account.akka.AccountBalanceActor
import org.gs.examples.account.http.actor.AccountBalanceRestfulRetriever._
import scala.collection.immutable.Set
import scala.concurrent.duration._

/** Sample and test code for the aggregator patter.
  * This is based on Jamie Allen's tutorial at
  * http://jaxenter.com/tutorial-asynchronous-programming-with-akka-actors-46220.html
  */

class AccountBalanceRestfulRetriever(actors: Map[AccountType, Props]) extends AccountBalanceActor
  with AccountRestfulPartialFunctions {

  import context._

  override def preStart() = {
    for ((k, v) <- actors) log.debug(s"key:$k, value:$v")
  }

  expectOnce {
    case GetCustomerAccountBalances(id, types) ⇒
      new AccountAggregator(sender(), id, actors)
    case mistake ⇒
      log.error(s"Expected GetCustomerAccountBalances found:$mistake")
      sender() ! CantUnderstand
      stop(self)
  }

  override val supervisorStrategy = 
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1.second) {
    case _: ArithmeticException => SupervisorStrategy.Resume
    case _: NullPointerException => SupervisorStrategy.Restart
    case _: IllegalArgumentException => SupervisorStrategy.Stop
    case _: Exception => SupervisorStrategy.Escalate
  }
}

object AccountBalanceRestfulRetriever {
  def createProps(actors: Map[AccountType, Props]): Props = {
    Props(classOf[AccountBalanceRestfulRetriever], actors)
  }

  case class GetCustomerAccountBalances(id: Long, accountTypes: Set[AccountType])
}