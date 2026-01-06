package web.utils

import de.androidpit.colorthief.ColorThief

import java.awt.{Color, Font, RenderingHints}
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Palette maker */
object PaletteMaker {


  /**
   *
   * @param imagePath input file
   * @param colorCount # of colors to get
   * @return Either[Throwable, Array[Array[Int]]]
   *  the palette as array of RGB arrays, exception otherwise
   */
  def getPalette(imagePath: String, colorCount: Int): Either[Throwable, Array[Array[Int]]] = {
    try {
      val image = ImageIO.read(new File(imagePath))
      val palette = Option(ColorThief.getPalette(image, colorCount))
      palette.toRight(new RuntimeException("No colors extracted"))
    }catch {
      case t: Throwable => Left(t)
    }
  }

  /**
   * Creates a palette image
   * @param colors list of rgb tuples
   * @param outputPath output file path
   * @return Either[Throwable, Boolean]  true if successful, exception otherwise
   */
  def drawPalette(colors: List[(Int, Int, Int)], outputPath: String): Either[Throwable, Boolean] = {
    if (colors.isEmpty)
      Left(new RuntimeException("Empty color list"))
    else{
      try {
        val hexColors = colors.map { case (r, g, b) => f"#$r%02x$g%02x$b%02x" }

        val output = new File(outputPath)
        val n = colors.size
        val blockWidth = 160
        val blockHeight = 100
        val labelHeight = 40
        val imgWidth = n * blockWidth
        val imgHeight = blockHeight + labelHeight

        val img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()

        val fontFile = new File("src/main/resources/plus-jakarta.ttf")
        val baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile)
        val font = baseFont.deriveFont(Font.BOLD, 18f)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setFont(font)

        for (((color, hex), i) <- colors.zip(hexColors).zipWithIndex) {
          val (r, gVal, b) = color
          val x = i * blockWidth

          g.setColor(new Color(r, gVal, b))
          g.fillRect(x, 0, blockWidth, blockHeight)

          val textColor =  Color.WHITE
          g.setColor(textColor)

          val metrics = g.getFontMetrics
          val textWidth = metrics.stringWidth(hex)
          val textHeight = metrics.getHeight

          val textX = x + (blockWidth - textWidth) / 2
          val textY = blockHeight + (labelHeight + textHeight) / 2 - metrics.getDescent

          g.drawString(hex.toUpperCase(), textX, textY)
        }
        g.dispose()
        ImageIO.write(img, "png", output)
        Right(true)
      } catch {
        case t: Throwable => Left(t)
      }
    }

  }
}