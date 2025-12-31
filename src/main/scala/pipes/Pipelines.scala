package pipes

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.webp.WebpWriter
import web.common.Common
import web.utils.Utils
import web.utils.Utils.{ImageFormat, ThumbType}
import web.utils.Utils.OCR.ContrastLevel

import java.io.File
import scala.util.Try

trait Pipe
trait IndependentPipe extends Pipe{
  def run(): Seq[Either[String, File]]
}
trait TransformationPipe extends Pipe{
  def rename(out: File, img: File, processed: ImmutableImage): File
  def run(): Seq[File]
}

/**
 * Behaves more like a batch than a pipeline.
 * Results are independent of one another
 * @param inputDir input directory
 * @param outputDir output directory
 * @param steps vector of batch steps
 */
final class AssetPipeline private(
                                   inputDir: Option[File],
                                   outputDir: Option[File],
                                   steps: Vector[(Seq[File], File) => Seq[Either[String, File]]]
                                 )   extends IndependentPipe{

  def outputTo(path: String): AssetPipeline =
    new AssetPipeline(inputDir, Some(new File(path)), steps)

  def convertTo(format: ImageFormat): AssetPipeline =
    new AssetPipeline(
      inputDir,
      outputDir,
      steps :+ { (images, out) =>
        Utils.convertTo(images, out, format)
      }
    )

  def thumbnails(kind: ThumbType): AssetPipeline =
    new AssetPipeline(
      inputDir,
      outputDir,
      steps :+ { (images, out) =>
        Utils.createThumbnail(images, out, kind)
      }
    )

  override def run(): Seq[Either[String, File]] = {
    val in = inputDir.getOrElse(sys.error("Input dir not set"))
    val out = outputDir.getOrElse(sys.error("Output dir not set"))

    out.mkdirs()

    val images = Utils.listImages(in)

    steps.flatMap(step => step(images, out))
  }
}

/**
 * Companion object for [[AssetPipeline]]
 */
object AssetPipeline {
  def from(path: String): AssetPipeline =
    new AssetPipeline(Some(new File(path)), None, Vector.empty)
}

/**
 * True pipeline.
 * Result dependent
 * @param inputDir input directory
 * @param outputDir output directory
 * @param steps transformations
 */
final class OCRPipeline private(
                                 inputDir: Option[File],
                                 outputDir: Option[File],
                                 steps: Vector[(Seq[File], File) => Seq[File]]
                               ) extends TransformationPipe{

  def outputTo(path: String): OCRPipeline =
    new OCRPipeline(inputDir, Some(new File(path)), steps)

  def grayscale(): OCRPipeline = {
    new OCRPipeline(
      inputDir,
      outputDir,
      steps :+ { (images, out) =>
        images.flatMap { img =>
          Try{
            val image = ImmutableImage.loader().fromFile(img)
            val processed = Utils.OCR.grayscale(image)
            rename(out, img, processed)
          }.toOption
        }
      }
    )
  }

  def binarize(threshold : Int): OCRPipeline = {
    new OCRPipeline(
      inputDir,
      outputDir,
      steps :+ { (images, out) =>
        images.flatMap { img =>
          Try{
            val image = ImmutableImage.loader().fromFile(img)
            val processed = Utils.OCR.binarize(image,threshold)
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
          Try{
            val image = ImmutableImage.loader().fromFile(img)
            val processed = Utils.OCR.contrast(image, level)
            rename(out, img, processed)
          }.toOption
        }
      }
    )
  }

  def rotate( radians: Double): OCRPipeline = {
    new OCRPipeline(
      inputDir,
      outputDir,
      steps :+ { (images, out) =>
        images.flatMap { img =>
          Try{
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
        Try{
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
    val key = Common.timestamp //Instant.now().toString
    val outPath = new File(out, s"${name}_$key${ext.getOrElse("")}").getPath
    processed.output(WebpWriter.MAX_LOSSLESS_COMPRESSION, new File(outPath))
  }

  def run(): Seq[File] = {
    val in = inputDir.getOrElse(sys.error("Input dir not set"))
    val out = outputDir.getOrElse(sys.error("Output dir not set"))

    out.mkdirs()

    val images = Utils.listImages(in)

    steps.foldLeft(images) { (acc, step) =>
      step(acc, out)
    }
  }
}

/**
 * Companion object for [[OCRPipeline]]
 */
object OCRPipeline {
  def from(path: String): OCRPipeline =
    new OCRPipeline(Some(new File(path)), None, Vector.empty)
}

