package expression

import org.specs2.Specification

import XMLDeserializer.expression
import hl7.v2.instance.Number
import hl7.v2.instance.Text

/**
  * Expression XML Deserialization specification
  * 
  * @author Salifou Sidi M. Malick <salifou.sidi@gmail.com>
  */

class XMLDeserializationSpec extends Specification { def is = s2"""
  Expression deserialization specification
    Deserialization of presence expression should work as expected        $pe1
    Deserialization of path value expression should work as expected      $pe2
    Deserialization of plain text expression should work as expected      $pe3
    Deserialization of format expression should work as expected          $pe4
    Deserialization of number list expression should work as expected     $pe5
    Deserialization of string list expression should work as expected     $pe6
    Deserialization of simple value expression should work as expected    $pe7
    Deserialization of and expression should work as expected             $pe8
    Deserialization of or expression should work as expected              $pe9
    Deserialization of not expression should work as expected             $pe10
    Deserialization of xor expression should work as expected             $pe11
    Deserialization of imply expression should work as expected           $pe12
    Deserialization of exist expression should work as expected           $pe13
    Deserialization of forall expression should work as expected          $pe14
  """

  import XMLDeserializer.expression

  def pe1 = expression( <Presence Path="1[1]"/> ) === Presence( "1[1]" ) 

  def pe2 = expression( <PathValue Path1="1[1]" Operator="EQ" Path2="1[1]"/> ) === PathValue( "1[1]", Operator.EQ, "1[1]")

  def pe3 = pe31 and pe32 and pe33 and pe34
  def pe31 = expression( <PlainText Path="1[1]" Text="XX" IgnoreCase="true"/> )  === PlainText( "1[1]", "XX", true)
  def pe32 = expression( <PlainText Path="1[1]" Text="XX" IgnoreCase="1"/> )     === PlainText( "1[1]", "XX", true)
  def pe33 = expression( <PlainText Path="1[1]" Text="XX" IgnoreCase="false"/> ) === PlainText( "1[1]", "XX", false)
  def pe34 = expression( <PlainText Path="1[1]" Text="XX" IgnoreCase="0"/> )     === PlainText( "1[1]", "XX", false)

  def pe4 = expression( <Format Path="1[1]" Regex="XX"/> ) === Format( "1[1]", "XX")

  def pe5 = expression( <NumberList Path="1[1]" CSV=" 1 , 2.0 , 3 "/> ) === NumberList( "1[1]", List(1, 2.0, 3))

  def pe6 = expression( <StringList Path="1[1]" CSV="1,2"/> ) === StringList( "1[1]", List("1", "2") )

  def pe7 = {
    val e1 = expression( <SimpleValue Path="1[1]" Operator="NE" Value="XX"/> )
    val e2 = expression( <SimpleValue Path="1[1]" Operator="NE" Value="XX" Type="Number"/> )
    e1 === SimpleValue( "1[1]", Operator.NE, Text("XX") ) and e2 === SimpleValue( "1[1]", Operator.NE, Number("XX") )
  }

  def pe8 = expression( <AND><Presence Path="1[1]"/><Presence Path="2[2]"/></AND> ) === AND( Presence("1[1]"), Presence("2[2]") )

  def pe9 = expression( <OR><Presence Path="1[1]"/><Presence Path="2[2]"/></OR> ) === OR( Presence("1[1]"), Presence("2[2]") )

  def pe10 = expression( <NOT><Presence Path="1[1]"/></NOT> ) === NOT( Presence("1[1]") )

  def pe11 = expression( <XOR><Presence Path="1[1]"/><Presence Path="2[2]"/></XOR> ) === XOR( Presence("1[1]"), Presence("2[2]") )

  def pe12 = expression( <IMPLY><Presence Path="1[1]"/><Presence Path="2[2]"/></IMPLY> ) === IMPLY( Presence("1[1]"), Presence("2[2]") )

  def pe13 = expression( <EXIST><Presence Path="1[1]"/><Presence Path="2[2]"/></EXIST> ) === EXIST( Presence("1[1]"), Presence("2[2]") )

  def pe14 = expression( <FORALL><Presence Path="1[1]"/><Presence Path="2[2]"/></FORALL> ) === FORALL( Presence("1[1]"), Presence("2[2]") )

  private implicit def toXOM( e: scala.xml.Node ): nu.xom.Element = {
    val r = new nu.xom.Element( e.label )
    // process the attributes
    e.attributes.asAttrMap foreach { t => r.addAttribute( new nu.xom.Attribute(t._1, t._2) ) }
    // process the children
    e.child foreach{ c => r.appendChild( toXOM( c ) ) }
    r
  }
}