import web.utils.Utils

import java.awt.Color
import java.io.File

class ImageUtilsTests extends munit.FunSuite {

  val inputDir  = new File("input")
  val outputDir = new File("output")

  if (!outputDir.exists()) outputDir.mkdirs()

  val images: Seq[File] =
    if (inputDir.exists()) Utils.listImages(inputDir)
    else Seq.empty


  test("input directory must exist") {
    assert(inputDir.exists(), s"input directory does not exist: ${inputDir.getAbsolutePath}")
  }

  test("there should be at least one image in input") {
    assert(images.nonEmpty, "No images found in input folder")
  }

  test("convert images to WebP successfully") {
    val results = Utils.convertToWebp(images, outputDir)

    val errors = results.collect { case Left(err) => err }

    assertEquals(errors.length, 0, s"Conversion errors: ${errors.mkString(", ")}")
  }


  test("desktop thumbnails created successfully") {
    val thumbs = Utils.createThumbnail(images, outputDir, "desktop")

    val okFiles = thumbs.collect { case Right(f) => f }
    val errors  = thumbs.collect { case Left(e) => e }

    assertEquals(errors.length, 0, s"Errors: ${errors.mkString(", ")}")
    assertEquals(okFiles.length, images.length)
  }


  test("mobile thumbnails created successfully") {
    val thumbs = Utils.createThumbnail(images, outputDir, "mobile")

    val okFiles = thumbs.collect { case Right(f) => f }
    val errors  = thumbs.collect { case Left(e) => e }

    assertEquals(errors.length, 0, s"Errors: ${errors.mkString(", ")}")
    assertEquals(okFiles.length, images.length)
  }


  test("placeholders generated successfully") {
    val placeholders = Utils.generatePlaceholders(
      number     = 3,
      width      = 200,
      height     = 200,
      fillColor  = Some(Color.RED),
      applyBlur  = false,
      outputDir  = outputDir
    )

    val okFiles = placeholders.collect { case Right(f) => f }
    val errors  = placeholders.collect { case Left(e) => e }

    assertEquals(errors.length, 0, s"Errors: ${errors.mkString(", ")}")
    assertEquals(okFiles.length, 3)
  }
}
