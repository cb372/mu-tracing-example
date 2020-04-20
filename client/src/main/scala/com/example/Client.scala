package com.example

import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import cats.effect.Console.io._
import com.example.hello._
import higherkindness.mu.rpc._
import higherkindness.mu.rpc.channel._
import fs2._
import natchez._
import io.grpc._

object Client extends IOApp {

  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    import natchez.jaeger.Jaeger
    import io.jaegertracing.Configuration.SamplerConfiguration
    import io.jaegertracing.Configuration.ReporterConfiguration
    Jaeger.entryPoint[F]("my-client-application") { c =>
      Sync[F].delay {
        c.withSampler(new SamplerConfiguration().withType("const").withParam(1))
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  val channelFor: ChannelFor = ChannelForAddress("localhost", 12345)

  val serviceClient: Resource[IO, Greeter[Kleisli[IO, Span[IO], *]]] =
    Greeter.tracingClient[IO](channelFor)

  val entrypointResource: Resource[IO, EntryPoint[IO]] = entryPoint

  def run(args: List[String]): IO[ExitCode] =
    for {
      _          <- putStr("Please enter your name: ")
      name       <- readLn
      response   <-
        serviceClient.use { client =>
          entrypointResource.use { ep =>
            // Send a few requests just to warm up the JVM.
            // The traces for the first couple of requests will look really slow.
            sendUnaryRequest(client, ep, name) >>
            sendUnaryRequest(client, ep, name) >>
            sendUnaryRequest(client, ep, name) >>
            sendUnaryRequest(client, ep, name) >>
            sendUnaryRequest(client, ep, name)

            // To try a client-streaming call, comment out the lines above
            // and uncomment the line below

            //sendClientStreamingRequest(client, ep, name)

            // Or try a server-streaming call:
            //sendServerStreamingRequest(client, ep, name)

            // Or a bidirectional streaming call:
            //sendBidirectionalStreamingRequest(client, ep, name)
          }
        }
      serverMood = if (response.happy) "happy" else "unhappy"
      _          <- putStrLn(s"The $serverMood server says '${response.greeting}'")
    } yield ExitCode.Success

  def sendUnaryRequest(
    client: Greeter[Kleisli[IO, Span[IO], *]],
    ep: EntryPoint[IO],
    name: String
  ): IO[HelloResponse] =
    ep.root("Client application root span (unary call)").use { span =>
      client.SayHello(HelloRequest(name)).run(span)
    }

  def sendClientStreamingRequest(
    client: Greeter[Kleisli[IO, Span[IO], *]],
    ep: EntryPoint[IO],
    name: String
  ): IO[HelloResponse] = {
    val stream = Stream[Kleisli[IO, Span[IO], *], HelloRequest](
      HelloRequest(name),
      HelloRequest(name)
    )

    ep.root("Client application root span (client-streaming call)").use { span =>
      client.ClientStreaming(stream).run(span)
    }
  }

  def sendServerStreamingRequest(
    client: Greeter[Kleisli[IO, Span[IO], *]],
    ep: EntryPoint[IO],
    name: String
  ): IO[HelloResponse] =
    ep.root("Client application root span (server-streaming call)").use { span =>
      client.ServerStreaming(HelloRequest(name)).run(span)
        .flatMap(_.compile.lastOrError.run(span))
    }

  def sendBidirectionalStreamingRequest(
    client: Greeter[Kleisli[IO, Span[IO], *]],
    ep: EntryPoint[IO],
    name: String
  ): IO[HelloResponse] = {
    val stream = Stream[Kleisli[IO, Span[IO], *], HelloRequest](
      HelloRequest(name),
      HelloRequest(name)
    )

    ep.root("Client application root span (bidirectional-streaming call)").use { span =>
      client.BidirectionalStreaming(stream).run(span)
        .flatMap(_.compile.lastOrError.run(span))
    }
  }

}
