package com.example

import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import com.example.happy._
import higherkindness.mu.rpc.protocol._
import higherkindness.mu.rpc.server._
import natchez._
import io.grpc._

object ServerB extends IOApp {

  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    import natchez.jaeger.Jaeger
    import io.jaegertracing.Configuration.SamplerConfiguration
    import io.jaegertracing.Configuration.ReporterConfiguration
    Jaeger.entryPoint[F]("ServiceB") { c =>
      Sync[F].delay {
        c.withSampler(new SamplerConfiguration().withType("const").withParam(1))
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    entryPoint[IO].use { ep =>
      implicit val service: HappinessService[Kleisli[IO, Span[IO], *]] =
        new MyHappinessService[Kleisli[IO, Span[IO], *]]
      for {
        serviceDef <- HappinessService.bindTracingService[IO](ep)
        server     <- GrpcServer.default[IO](12346, List(AddService(serviceDef)))
        _          <- GrpcServer.server[IO](server)
      } yield ExitCode.Success
    }
  }

}
