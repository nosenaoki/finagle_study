package jp.co.imj.proto

import com.twitter.concurrent.Broker
import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.concurrent.Offer
import com.twitter.util.Future
import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http.HttpRequest
import com.twitter.finagle.stream.StreamResponse
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.buffer.ChannelBuffers
import java.nio.charset.Charset
import com.twitter.finagle.builder.Server
import com.twitter.finagle.builder.ServerBuilder
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.HttpResponse
import com.twitter.finagle.http.Http

class StreamServer {
  val messages = new Broker[String]
  val addBroker = new Broker[Broker[String]]
  val remBroker = new Broker[Broker[String]]
  
case class Message(from:String, msg:String)
  
  private[this] def tee(receivers: Set[Broker[String]]) {
    Offer.select(
      addBroker.recv { b => tee(receivers + b) },
      remBroker.recv { b => tee(receivers - b) },
      if (receivers.isEmpty) Offer.never else {
        messages.recv { m =>
          Future.join(receivers map { _ ! m } toSeq) ensure tee(receivers)
        }
      }
    )
  }

  tee(Set())

  def start() {
    val streamService = new Service[HttpRequest, StreamResponse] {
      def apply(request: HttpRequest) = Future {
        val subscriber = new Broker[String]
        addBroker ! subscriber
        new StreamResponse {
          val httpResponse = new DefaultHttpResponse(
            request.getProtocolVersion, HttpResponseStatus.OK)

          httpResponse.setHeader("Content-Type", "text/html")
          httpResponse.setHeader("Cache-Control", "no-cache, must-revalidate")
          httpResponse.setHeader("Expires", "Mon, 26 Jul 1997 05:00:00 GMT")
          
          def messages = {
            subscriber.recv.map { msg => 
              ChannelBuffers.copiedBuffer(msg, Charset.forName("UTF-8"))
            }
          }
          def error = new Broker[Throwable].recv
          def release() = {
            remBroker ! subscriber
            // sink any existing messages, so they
            // don't hold up the upstream.
            subscriber.recv foreach { _ => () }
          }
        }
      }
    }

    val streamServer: Server = ServerBuilder()
       .codec(com.twitter.finagle.stream.Stream())
       .bindTo(new InetSocketAddress(9000))
       .name("streamserver")
       .build(streamService)
    
    val rpcService = new Service[String, String] {
      def apply(request:String) = {
        messages ! request
        Future.value(request)
      }
    }

    val rpcServer: Server = ServerBuilder()
	    .codec(StringCodec)
	    .bindTo(new InetSocketAddress(9001))
	    .name("rpcserver")
	    .build(rpcService)
  }
}