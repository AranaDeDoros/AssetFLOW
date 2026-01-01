import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.webp.WebpWriter
import web.common.Common
import web.utils.Utils
import web.utils.Utils.{ImageFormat, ThumbType}
import web.utils.Utils.OCR.ContrastLevel

import java.io.File
import scala.util.Try

package object pipes {

  private[pipes] def prepareIO(
                                inputDir: Option[File],
                                outputDir: Option[File]
                              ): (Seq[File], File) = {

    val in = inputDir.getOrElse(sys.error("Input dir not set"))
    val out = outputDir.getOrElse(sys.error("Output dir not set"))

    out.mkdirs()

    val images = Utils.listImages(in)
    (images, out)
  }

  private type BatchStep = (Seq[File], File) => Seq[Either[String, File]]
  private type TransformStep = (Seq[File], File) => Seq[File]

  trait Pipe {
    def inspect(f: Seq[File] => Unit): Pipe

    def dryRun(): Unit
  }

  trait IndependentPipe extends Pipe {
    def run(): Seq[Either[String, File]]
  }

  trait TransformationPipe extends Pipe {
    def rename(out: File, img: File, processed: ImmutableImage): File

    def run(): Seq[File]

  }

  /**
   * Batch processor
   * Results are independent of one another
   *
   * @param inputDir  input directory
   * @param outputDir output directory
   * @param steps     vector of batch steps
   */
  final class AssetBatch private(
                                  inputDir: Option[File],
                                  outputDir: Option[File],
                                  steps: Vector[BatchStep]
                                ) extends IndependentPipe {

    def stepsNumber: Int = steps.size
    def outputTo(path: String): AssetBatch =
      new AssetBatch(inputDir, Some(new File(path)), steps)

    def convertTo(format: ImageFormat): AssetBatch =
      new AssetBatch(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          Utils.convertTo(images, out, format)
        }
      )

    def thumbnails(kind: ThumbType): AssetBatch =
      new AssetBatch(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          Utils.createThumbnail(images, out, kind)
        }
      )

    override def run(): Seq[Either[String, File]] = {
      val (images, out) = prepareIO(inputDir, outputDir)
      steps.flatMap(step => step(images, out))
    }

    override def inspect(f: Seq[File] => Unit): AssetBatch = {
      new AssetBatch(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          f(images)
          images.map(Right(_))
        }
      )
    }

    override def dryRun(): Unit = {
      val in = inputDir.getOrElse(sys.error("Input dir not set"))
      val images = Utils.listImages(in)
      println(s"Would process ${images.size} images with ${steps.size} steps")
    }


  }

  /**
   * Companion object for [[AssetBatch]]
   */
  object AssetBatch {
    def from(path: String): AssetBatch =
      new AssetBatch(Some(new File(path)), None, Vector.empty)
  }

  /**
   * True pipeline.
   * Result dependent
   * Failed transformations drop the image.
   *
   * @param inputDir  input directory
   * @param outputDir output directory
   * @param steps     transformations
   */
  final class OCRPipeline private(
                                   inputDir: Option[File],
                                   outputDir: Option[File],
                                   steps: Vector[TransformStep]
                                 ) extends TransformationPipe {

    def stepsNumber: Int = steps.size

    def outputTo(path: String): OCRPipeline =
      new OCRPipeline(inputDir, Some(new File(path)), steps)

    def grayscale(): OCRPipeline = {
      new OCRPipeline(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          images.flatMap { img =>
            Try {
              val image = ImmutableImage.loader().fromFile(img)
              val processed = Utils.OCR.grayscale(image)
              rename(out, img, processed)
            }.toOption
          }
        }
      )
    }

    def binarize(threshold: Int): OCRPipeline = {
      new OCRPipeline(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          images.flatMap { img =>
            Try {
              val image = ImmutableImage.loader().fromFile(img)
              val processed = Utils.OCR.binarize(image, threshold)
              rename(out, img, processed)
            }.toOption
          }
        }
      )
    }

    def contrast(level: ContrastLevel): OCRPipeline = {
      new OCRPipeline(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          images.flatMap { img =>
            Try {
              val image = ImmutableImage.loader().fromFile(img)
              val processed = Utils.OCR.contrast(image, level)
              rename(out, img, processed)
            }.toOption
          }
        }
      )
    }

    def rotate(radians: Double): OCRPipeline = {
      new OCRPipeline(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          images.flatMap { img =>
            Try {
              val image = ImmutableImage.loader().fromFile(img)
              val processed = Utils.OCR.rotate(image, radians)
              rename(out, img, processed)
            }.toOption
          }
        }
      )
    }

    def optimize(tilt: Double = 0.0,
                 contrastFactor: Double = 1.4,
                 threshold: Int = 128,
                 doBinarize: Boolean = true) = new OCRPipeline(
      inputDir,
      outputDir,
      steps :+ { (images, out) =>
        images.flatMap { img =>
          Try {
            val image = ImmutableImage.loader().fromFile(img)
            val processed = Utils.OCR.optimize(
              image,
              tilt,
              contrastFactor,
              threshold,
              doBinarize
            )
            rename(out, img, processed)
          }.toOption
        }
      }
    )

    override def rename(out: File, img: File, processed: ImmutableImage): File = {
      val (name, ext) = Common.getNameAndExtension(img.getName)
      val key = "" //s"_Common.timestamp"
      val outPath = new File(out, s"${name}$key.${ext.getOrElse("")}").getPath
      processed.output(WebpWriter.MAX_LOSSLESS_COMPRESSION, new File(outPath))
    }

    override def run(): Seq[File] = {
      val (images, out) = prepareIO(inputDir, outputDir)
      steps.foldLeft(images) { (acc, step) =>
        step(acc, out)
      }
    }

    override def inspect(f: Seq[File] => Unit): OCRPipeline = {
      new OCRPipeline(
        inputDir,
        outputDir,
        steps :+ { (images, out) =>
          f(images)
          images
        }
      )
    }

    override def dryRun(): Unit = {
      val in = inputDir.getOrElse(sys.error("Input dir not set"))
      val images = Utils.listImages(in)
      println(s"Would process ${images.size} images with ${steps.size} steps")
    }


  }

  /**
   * Companion object for [[OCRPipeline]]
   */
  object OCRPipeline {
    def from(path: String): OCRPipeline =
      new OCRPipeline(Some(new File(path)), None, Vector.empty)
  }

}