package controllers

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.mvc._

/**
 * Created by cookeem on 15/12/12.
 */
case class Validations (
                         username: String,
                         firstName: String,
                         number: Int,
                         score: Int,
                         host: String,
                         age: Option[Int],
                         notes: Option[String]
                         )

class FormApp extends Controller {
  val validationsForm = Form(
    mapping(
      "username" -> nonEmptyText(5, 20),
      "firstName" -> text(1, 20),
      "number" -> number(1, 5),
      "score" -> number.verifying(min(1), max(100)),
      "host" -> nonEmptyText.verifying(pattern("[a-z]+".r, "One or more lowercase characters", "Error")),
      "age" -> optional(number),
      "notes" -> optional(text)
    )(Validations.apply)(Validations.unapply)
      verifying("If age is given, it must be greater than zero", model =>
      model.age match {
        case Some(age) => age < 0
        case None => true
      }
      )
  )

  def add = Action {
    Ok(views.html.form.form1(validationsForm))
  }

  /**
   * Handle the 'add' form submission.
   */
  def save = Action { implicit request =>
    println(request.method+','+request.body.asFormUrlEncoded)
    val formData = request.body.asFormUrlEncoded.getOrElse(Map())
    import scala.collection.mutable.ArrayBuffer
    val ArrayBuffer(a) = formData("score")
    val ArrayBuffer(b) = formData("number")
    println(s"""output: formData("score"): ${a} ${a.getClass}, formData("number"): ${b} ${b.getClass}""")
    validationsForm.bindFromRequest.fold(
      errors => BadRequest(views.html.form.form1(errors)),
      stock => {
        // would normally do a 'save' here
        Redirect(routes.FormApp.save())
      }
    )
  }

}
