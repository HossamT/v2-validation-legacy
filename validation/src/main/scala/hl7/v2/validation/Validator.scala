package hl7.v2.validation

import expression.{Plugin, EvalResult}
import hl7.v2.instance.{Separators, Element}
import hl7.v2.parser.Parser
import hl7.v2.profile.Profile
import hl7.v2.validation.report.Report

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Trait defining the message validation 
  * 
  * @author Salifou Sidi M. Malick <salifou.sidi@gmail.com>
  */

trait Validator { this: Parser with structure.Validator
                               with content.Validator
                               with vs.Validator =>

  val profile: Profile

  /**
    * Validates the message using the mixed in structure,
    * content and value set validators and returns the report.
    * @param message - The message to be validated
    * @param id      - The id of the message as defined in the profile
    * @return The validation report
    */
  def validate( message: String, id: String ): Future[Report] =
    profile.messages get id match {
      case None =>
        val msg = s"No message with id '$id' is defined in the profile"
        Future failed new Exception(msg)
      case Some( model ) => 
        parse( message, model ) match {
          case Success( m ) => 
            val structErrors   = checkStructure( m )
            val contentErrors  = checkContent  ( m )
            val valueSetErrors = checkValueSet ( m )
            for {
              r1 <- structErrors
              r2 <- contentErrors
              r3 <- valueSetErrors
            } yield Report(r1, r2, r3)
          case Failure(e) => Future failed e
        }
    }
}

/**
  * An HL7 message validator which uses an empty value set validator
  * and the default implementation of the parser, structure validator,
  * content validator and expression evaluator.
  */
class HL7Validator(
    val profile: Profile,
    val constraintManager: content.ConstraintManager,
    val pluginMap: Map[String, (Plugin, Element, Separators) => EvalResult]
  ) extends Validator
    with hl7.v2.parser.impl.DefaultParser
    with structure.DefaultValidator
    with content.DefaultValidator
    with vs.EmptyValidator
    with expression.DefaultEvaluator