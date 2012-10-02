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

class ChatServer {
  val messages = new Broker[Message]
  val addBroker = new Broker[Broker[Message]]
  val remBroker = new Broker[Broker[Message]]
  
case class Message(from:String, msg:String)
  
  private[this] def tee(receivers: Set[Broker[Message]]) {
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
        val subscriber = new Broker[Message]
        addBroker ! subscriber
        new StreamResponse {
          val httpResponse = new DefaultHttpResponse(
            request.getProtocolVersion, HttpResponseStatus.OK)
          httpResponse.setHeader("Content-Type", "text/html")
          
          def messages = {
            subscriber.recv.map { msg => 
              //val script = "<script>parent.onMessage('" + msg.from + "','" + msg.msg + "')</script>"
              val script = "<script>alert('" + msg.toString() + "');</script>"
              ChannelBuffers.copiedBuffer(script, Charset.forName("UTF-8"))
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
    
    val rpcService = new Service[HttpRequest, HttpResponse] {
      def apply(request:HttpRequest) = Future {
        val data = request.getContent().toString(Charset.forName("UTF-8"))
        val Array(user, message) = data.split(',').map(_.trim)
        messages ! Message(user, message)
        new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK)
      }
    }

    val rpcServer: Server = ServerBuilder()
	    .codec(Http())
	    .bindTo(new InetSocketAddress(9001))
	    .name("rpcserver")
	    .build(rpcService)
  }
}