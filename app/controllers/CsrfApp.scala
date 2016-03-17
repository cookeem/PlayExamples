package controllers


import javax.inject.Inject

import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc._
import play.filters.csrf.{CSRFCheck, CSRFAddToken}

/**
 * Created by cookeem on 16/1/14.
 */
case class LoginData(username: String, password: String)
class CsrfApp @Inject() (implicit addToken: CSRFAddToken, checkToken: CSRFCheck) extends Controller {
  val loginForm = Form (
    mapping (
      "username" -> nonEmptyText(5, 12),
      "password" -> nonEmptyText(6, 20)
    )(LoginData.apply)(LoginData.unapply)
  )

  def csrfToPost = addToken {
    Action { implicit request =>
      Ok(views.html.form.csrf(loginForm))
    }
  }

  def csrfPost = checkToken {
    Action {implicit request =>
      val a = loginForm.bindFromRequest()
      Ok(s"loginForm: ${a.data}")
    }
  }
}
