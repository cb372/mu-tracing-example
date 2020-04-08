package com.example

import cats._
import cats.implicits._
import cats.data.Kleisli
import cats.effect._
import com.example.happy._
import natchez._
import scala.concurrent.duration._

class MyHappinessService[F[_]: Applicative: Trace] extends HappinessService[F] {

  def CheckHappiness(req: HappinessRequest): F[HappinessResponse] =
    Trace[F].span("check happiness") {
      HappinessResponse(happy = true).pure[F]
    }

}
