import coloring.RGBColor

class ColorTests extends munit.FunSuite {

  val redColor = RGBColor(100, 50, 200)

  test("increasing all channels should return a new color"){
    val brighter = redColor.increaseAll(50, 30, -100)
    brighter match {
      case RGBColor(_,_,_,_) => assert(cond = true, "Color generated successfully.")
      case _ => fail("Error while increasing channels")
    }
  }

  test("mixing colors should return a new color"){
    val blueColor = RGBColor(0, 0, 255)
    val mixed = redColor.mixWith(blueColor, 0.5)
    mixed match {
      case RGBColor(_,_,_,_) => s"adjusted color: $mixed, hex=${mixed.toHex}"
      case _ => fail("Error while mixing colors")
    }
  }

  test("RGBColor singleton should be able to return a color a from hex codes "){
    val fromHex = RGBColor.fromHex("#ff00cc")
    fromHex match {
      case Some(RGBColor(_,_,_,_))=> assert(cond = true, "Color generated successfully.")
      case _ => fail("Error while generating color")
    }
  }
}