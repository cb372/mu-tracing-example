package com.example

import fs2._
import cats.~>
import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import com.example.hello._
import com.example.happy._
import higherkindness.mu.rpc._
import higherkindness.mu.rpc.protocol._
import higherkindness.mu.rpc.server._
import higherkindness.mu.rpc.channel._
import natchez._
import io.grpc._

object ServerA extends IOApp {

  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    import natchez.jaeger.Jaeger
    import io.jaegertracing.Configuration.SamplerConfiguration
    import io.jaegertracing.Configuration.ReporterConfiguration
    Jaeger.entryPoint[F]("ServiceA") { c =>
      Sync[F].delay {
        c.withSampler(new SamplerConfiguration().withType("const").withParam(1))
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  val channelFor: ChannelFor = ChannelForAddress("localhost", 12346)

  val happinessServiceClient: Resource[IO, HappinessService[Kleisli[IO, Span[IO], *]]] =
    HappinessService.tracingClient[IO](channelFor)

  def run(args: List[String]): IO[ExitCode] = {
    entryPoint[IO].use { ep =>
      happinessServiceClient.use { client =>
        implicit val greeter: Greeter[Kleisli[IO, Span[IO], *]] =
          new MyGreeter[Kleisli[IO, Span[IO], *]](client)
        for {
          serviceDef <- Greeter.bindTracingService[IO](ep)
          server     <- GrpcServer.default[IO](12345, List(AddService(serviceDef)))
          _          <- GrpcServer.server[IO](server)
        } yield ExitCode.Success
      }
    }
  }

}
