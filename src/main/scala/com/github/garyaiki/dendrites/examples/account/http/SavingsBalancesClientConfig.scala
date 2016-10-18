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
package org.gs.examples.account.http

import akka.util.Timeout
import scala.concurrent.duration.MILLISECONDS
import org.gs.http.{configBaseUrl, configRequestPath, getHostConfig}

/** Read config for Savings Balances Client
  *
  * @author Gary Struthers
  *
  */
class SavingsBalancesClientConfig() {

  val hostConfig = getHostConfig("dendrites.savings-balances.http.interface",
    "dendrites.savings-balances.http.port")
  val config = hostConfig._1
  val baseURL = configBaseUrl("dendrites.savings-balances.http.path", hostConfig)
  val requestPath = configRequestPath("dendrites.savings-balances.http.requestPath", config)
  val timeout = new Timeout(config.getInt("dendrites.savings-balances.http.millis"),
      MILLISECONDS)
}