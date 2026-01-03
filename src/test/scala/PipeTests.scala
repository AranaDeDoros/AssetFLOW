import pipes.{AssetBatch, OCRPipeline}
import web.utils.ImageTransforms
import web.utils.ImageTransforms.{Desktop, Webp}

import java.io.File

class PipeTests extends munit.FunSuite {
  val inputDir  = new File("input")
  val outputDir = new File("output")

  if (!outputDir.exists()) outputDir.mkdirs()

  val images: Seq[File] =
    if (inputDir.exists()) ImageTransforms.listImages(inputDir)
    else Seq.empty

  val nImages = 3

  test("input directory must exist") {
    assert(inputDir.exists(), s"input directory does not exist: ${inputDir.getAbsolutePath}")
  }

  test("there should be at least one image in input") {
    assert(images.nonEmpty, "No images found in input folder")
  }

  test("batch process must be successful through all steps") {
  val batch = AssetBatch
    .from("input")
    .outputTo("output")
    .convertTo(Webp)
    .thumbnails(Desktop)
    val results = batch.run()
    val okFiles = results.collect { case Right(f) => f }
    val errors  = results.collect { case Left(e) => e }
    assertEquals(errors.length, 0, s"Errors: ${errors.mkString(", ")}")
    assertEquals(okFiles.length, images.length * batch.stepsNumber)
  }


  test("pipeline runs and produces output images") {
        val result =
          OCRPipeline
            .from(inputDir.toString)
            .outputTo(outputDir.toString)
            .grayscale()
            .binarize(128)
            .run()
        assert(result.nonEmpty, "Expected at least one output image")
        assert(result.forall(_.exists()), "All output files should exist")
        assert(result.forall(_.getParentFile == outputDir))

  }

  test("inspect observes images without modifying them") {
        var inspectedCount = 0
        val result =
          OCRPipeline
            .from(inputDir.toString)
            .outputTo(outputDir.toString)
            .inspect { images =>
              inspectedCount = images.size
            }
            .grayscale()
            .run()
        assertEquals(inspectedCount, nImages)
        assertEquals(result.size, nImages)
  }

  test("optimize step produces output") {
        val result =
          OCRPipeline
            .from(inputDir.toString)
            .outputTo(outputDir.toString)
            .optimize()
            .run()
        assertEquals(result.size, nImages)
  }

  test("dryRun does not throw when inputDir is set") {
      val pipeline =
        OCRPipeline
          .from(inputDir.toString)
          .grayscale()
          .binarize(120)
      pipeline.dryRun() // should not throw
      assert(true)
  }
}
