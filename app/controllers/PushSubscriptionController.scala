package controllers

import actions.AuthenticatedAction
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import services.PushSubscriptionService
import shared.api.{PushSubscriptionDeleteRequest, PushSubscriptionRequest}
import upickle.default.{read, write}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class PushSubscriptionController @Inject() (
    cc: ControllerComponents,
    authenticatedAction: AuthenticatedAction,
    service: PushSubscriptionService
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def config: Action[AnyContent] = Action {
    Ok(write(service.publicConfig)).as("application/json;charset=utf-8")
  }

  def subscribe: Action[String] = authenticatedAction.async(parse.tolerantText) { request =>
    Try(read[PushSubscriptionRequest](request.body)).toOption match {
      case Some(subscription) =>
        service
          .subscribe(request.userId, subscription)
          .map(status => Ok(write(status)).as("application/json;charset=utf-8"))
          .recover { case _: IllegalArgumentException => BadRequest("Invalid push subscription") }
      case None => Future.successful(BadRequest("Invalid push subscription"))
    }
  }

  def unsubscribe: Action[String] = authenticatedAction.async(parse.tolerantText) { request =>
    Try(read[PushSubscriptionDeleteRequest](request.body)).toOption match {
      case Some(subscription) =>
        service
          .unsubscribe(request.userId, subscription.endpoint)
          .map(status => Ok(write(status)).as("application/json;charset=utf-8"))
      case None => Future.successful(BadRequest("Invalid push subscription"))
    }
  }
}
