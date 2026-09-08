package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

@Singleton
class HealthController @Inject() (val controllerComponents: ControllerComponents) extends BaseController {
  def check: Action[AnyContent] = Action {
    Ok("ok").as("text/plain")
  }
}
