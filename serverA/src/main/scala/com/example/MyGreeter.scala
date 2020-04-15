package com.example

import fs2._
import cats._
import cats.implicits._
import cats.data.Kleisli
import cats.effect._
import com.example.hello._
import com.example.happy._
import natchez._
import scala.concurrent.duration._

class MyGreeter[F[_]: MonadError[*[_], Throwable]: Timer: Trace](happinessClient: HappinessService[F])(implicit compiler: Stream.Compiler[F, F]) extends Greeter[F] {

  def ClientStreaming(req: Stream[F, HelloRequest]): F[HelloResponse] =
    for {
      lastReq        <- req.compile.lastOrError
      happinessResp  <- happinessClient.CheckHappiness(HappinessRequest())
    } yield HelloResponse(s"Hello, streaming ${lastReq.name}!", happinessResp.happy)

  def ServerStreaming(req: HelloRequest): F[Stream[F, HelloResponse]] =
    happinessClient.CheckHappiness(HappinessRequest()).map { happinessResp =>
      Stream(
        HelloResponse(s"Hello, ${req.name}!", happinessResp.happy),
        HelloResponse(s"Hi again, ${req.name}!", happinessResp.happy)
      ).covary[F]
    }


  def SayHello(req: HelloRequest): F[HelloResponse] =
    for {
      cachedGreeting <- lookupGreetingInCache(req.name)
      greeting       <- cachedGreeting.fold(lookupGreetingInDBAndWriteToCache(req.name))(_.pure[F])
      happinessResp  <- happinessClient.CheckHappiness(HappinessRequest())
    } yield HelloResponse(greeting, happinessResp.happy)

  def lookupGreetingInCache(name: String): F[Option[String]] =
    Trace[F].span("lookup greeting in cache") {
      // simulate looking in Redis and not finding anything
      Timer[F].sleep(5.millis) *>
        Trace[F].put("cache_hit" -> TraceValue.BooleanValue(false)) *>
        none[String].pure[F]
    }

  def lookupGreetingInDB(name: String): F[String] =
    Trace[F].span("lookup greeting in DB") {
      // simulate reading the value from a DB
      Timer[F].sleep(100.millis) *>
        s"Hello, $name!".pure[F]
    }

  def writeGreetingToCache(name: String, greeting: String): F[Unit] =
    Trace[F].span("write greeting to cache") {
      // simulate writing the value to a cache
      Timer[F].sleep(5.millis)
    }

  def lookupGreetingInDBAndWriteToCache(name: String): F[String] =
    for {
      greeting <- lookupGreetingInDB(name)
      _ <- writeGreetingToCache(name, greeting)
    } yield greeting
}
