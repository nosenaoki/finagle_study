package jp.co.imj.proto

import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import java.net.InetSocketAddress
import com.twitter.concurrent.Broker
import com.twitter.concurrent.Offer
import com.twitter.finagle.stream.StreamResponse

object EchoClient {
  def main(args:Array[String]) {
    val client: Service[String, String] = ClientBuilder()
	    .codec(StringCodec)
	    .hosts(new InetSocketAddress(8080))
	    .hostConnectionLimit(1)
	    .build()

    client("hi alalalala\n") onSuccess { result => 
      println("hogehoge:" + result)
    } onFailure { error =>
      error.printStackTrace()
    } ensure {
      client.release()
    }
  }
}