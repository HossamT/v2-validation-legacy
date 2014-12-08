package hl7.v2.validation.structure

import hl7.v2.instance._
import hl7.v2.instance.util.{ ValueFormatCheckers => VFC }
import VFC.{ checkDate, checkDateTime, checkNumber, checkTime }
import hl7.v2.profile.{Range, Req, Usage}
import hl7.v2.validation.report._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Default implementation of the structure validation
  */
trait DefaultValidator extends Validator with EscapeSeqHandler {

  /**
    * Checks the message structure and returns the list of problems.
    *
    * @param m - The message to be checked
    * @return  - The list of problems
    */
  def checkStructure(m: Message): Future[List[SEntry]] = Future {
    implicit val s = m.separators
    (m.invalid, m.unexpected) match {
      case (Nil, Nil) => check(m.asGroup)
      case (xs , Nil) => InvalidLines(xs) :: check(m.asGroup)
      case (Nil,  xs) => UnexpectedLines(xs) :: check(m.asGroup)
      case (xs, ys) => InvalidLines(xs) :: UnexpectedLines(ys) :: check(m.asGroup)
    }
  }

  /**
    * Checks the element against the the specified requirements
    * and recursively check the children if applicable
    * @param e - The element to be checked
    * @param r - The requirements
    * @return A list of problems found
    */
  def check(e: Element, r: Req)(implicit sep: Separators): List[SEntry] =
    e match {
      case s: Simple  => check(s, r)
      case c: Complex => check(c)
    }

  /**
    * Checks the simple element against the specified requirements
    * @param s   - The simple element to be checked
    * @param req - The requirements
    * @return A list of problems found
    */
  def check(ss: Simple, req: Req)(implicit s: Separators): List[SEntry] =
    checkValue(ss.location, ss.value, req.length)

  /**
    * Checks the children of the complex element against their requirements
    * @param c - The complex element to be checked
    * @return A list of problems found
    */
  def check(c: Complex)(implicit s: Separators): List[SEntry] = {
    // Sort the children by position
    val map = c.children.groupBy( x => x.position )

    // Check every position defined in the model
    val r = c.reqs.foldLeft( List[SEntry]() ) { (acc, r) =>
      // Get the children at the current position (r.position)
      val children = map.getOrElse(r.position, Nil)

      //FIXME we are missing the description here ...
      val dl = c.location.copy(desc="...", path=s"${c.location.path}.${r.position}[1]")

      // Checks the usage
      checkUsage( r.usage, children )(dl) match {
        case Nil => // No usage error thus we can continue the validation
          // Check the cardinality
          val r1 = checkCardinality( children, r.cardinality )
          // Recursively check the children
          val r2 = children flatMap { check( _, r ) }
          r1 ::: r2 ::: acc
        case xs  => xs ::: acc // Usage problem no further check is necessary
      }
    }

    // Check for extra children and return the result
    checkExtra( c ) ::: r
  }

  /**
    * Returns a list of report entries if the usage is:
    *     1) R and the list of elements is empty
    *     2) X and the list of elements is not empty
    *     3) W and the list of elements is not empty
    *
    * @param u  - The usage
    * @param l  - The list of elements
    * @param dl - The default location
    * @return A list of report entries
    */
  def checkUsage(u: Usage, l: List[Element])(dl: Location): List[SEntry] =
    (u, l) match {
      case (Usage.R, Nil) => RUsage(dl) :: Nil
      case (Usage.X, xs ) => xs map { e => XUsage( e.location ) }
      case (Usage.W, xs ) => xs map { e => WUsage( e.location ) }
      case _              => Nil
    }

  /**
    * Returns a list of report entries for every element which instance
    * number is greater than the maximum range or a list with a single
    * element if the highest instance number is lower than the minimum range
    *
    * @param l     - The list of element
    * @param range - The cardinality range
    * @return A list of report entries
    */
  def checkCardinality(l: List[Element], range: Range): List[SEntry] =
    if( l.isEmpty ) Nil
    else {
      // The only reason this is needed is because of field repetition
      val highestRep = l maxBy instance
      val i = instance( highestRep )
      if( i < range.min ) MinCard( highestRep.location, i, range ) :: Nil
      else
        l filter { e => afterRange( instance(e), range ) } map { e =>
          MaxCard(e.location, instance(e), range)
        }
    }

  /**
    * Returns a list containing an extra entry
    * if the complex element has extra children.
    * @param c - The complex element
    * @return A list of report entries
    */
  def checkExtra(c: Complex): List[SEntry] =
    if( c.hasExtra ) Extra( c.location ) :: Nil else Nil

  private
  def checkCardinality(l: List[Element], or: Option[Range]): List[SEntry] =
    or match {
      case Some(r) => checkCardinality(l, r)
      case None    => Nil
    }

  /**
    * If the value is not Null then checks the length, the format
    * and the presence of separators if the value is not Null
    * @param l  - The location of the value
    * @param v  - The value
    * @param lc - The length constraint
    * @param s  - The separators
    * @return The list of violations
    */
  def checkValue(l: Location, v: Value, lc: Option[Range])
                (implicit s: Separators): List[SEntry] =
    v.isNull match {
      case true  => Nil
      case false => checkFormat(l, v).toList ::: checkLength(l, v, lc).toList
    }

  /**
    * Checks the length and returns the error if any
    * @param l  - The location
    * @param v  - The value
    * @param lc - The length constraint
    * @param s  - The separators
    * @return The error or None
    */
  def checkLength(l: Location, v: Value, lc: Option[Range])
                 (implicit s: Separators): Option[Length] =
    lc flatMap { range =>
      val raw = unescape( v.raw )
      if (inRange(raw.length, range)) None else Some( Length(l, raw, range) )
    }

  /**
    * Checks the format including the presence of separator
    * and returns the error if any
    * @param l - The location
    * @param v - The value
    * @param s - The separators
    * @return The error or None
    */
  def checkFormat(l: Location, v: Value)(implicit s: Separators): Option[SEntry] =
    v match {
      case Number(x)  => checkNumber(x) map { m => Format(l, m) }
      case Date(x)    => checkDate(x)   map { m => Format(l, m) }
      case Time(x, _) => checkTime(x)   map { m => Format(l, m) }
      case DateTime(x, _) => checkDateTime(x) map { m => Format(l, m) }
      case _ if containSeparators(v) => Some( UnescapedSeparators(l) )
      case _ => None
    }

  /**
    * Returns true if the value contain unescaped field,
    * component, sub-component or repetition separators
    */
  private def containSeparators(v: Value)(implicit s: Separators): Boolean =
    v.raw exists { c => c == s.fs || c == s.cs || c == s.ss || c == s.rs }

  /**
    * Returns the instance number of the element
    */
  private def instance(e: Element) = e.instance

  /**
    * Returns true if i is in the range
    */
  def inRange(i: Int, r: Range) = i >= r.min && (r.max == "*" || i <= r.max.toInt)

  /**
    * Returns true is i > Range.max
    */
  def afterRange(i: Int, r: Range) = if(r.max == "*") false else i > r.max.toInt

}
