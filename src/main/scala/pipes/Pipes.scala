import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.webp.WebpWriter
import web.utils.ImageTransforms
import web.utils.ImageTransforms.TransformationResult

import java.io.File

package object pipes {
  //type state
  sealed trait InState
  sealed trait OutState

  sealed trait InMissing extends InState
  sealed trait InPresent extends InState

  sealed trait OutMissing extends OutState
  sealed trait OutPresent extends OutState

  trait ObservableOp[A, Self] {
    def inspect(label: String)(f: A => Unit): Self
    def dryRun(): Unit
  }
  final case class TransformationStep( name: String,  run: ImmutableImage => ImmutableImage )

  object Pipe {
    def apply(): Pipe[InMissing, OutMissing] =
      new Pipe(None, None, Seq.empty)
  }

  final class Pipe [In <: InState, Out <: OutState]  private(
                             inputDir: Option[File],
                             outputDir: Option[File],
                             steps: Seq[TransformationStep] = Nil
                           ) extends ObservableOp[ImmutableImage, Pipe[In, Out]] {

    def from(path: String) (implicit ev: In =:= InMissing): Pipe[InPresent, Out] =
      new Pipe(Some(new File(path)), outputDir, steps)

    def to(path: String)(implicit ev: Out =:= OutMissing): Pipe[In, OutPresent] =
      new Pipe(inputDir, Some(new File(path)), steps)

    def step(step: TransformationStep): Pipe[In, Out] =
      new Pipe(inputDir, outputDir, steps :+ step)


    def run() (implicit ev1: In =:= InPresent, ev2: Out =:= OutPresent):  Seq[File] = {
      val in  = inputDir.get
      val out = outputDir.get
      out.mkdirs()

      val files = ImageTransforms.listImages(in)

      files.flatMap { file =>
        try {
          val initial = ImmutableImage.loader().fromFile(file)

          val finalImage =
            steps.foldLeft(initial) { (img, step) =>
              step.run(img)
            }

          val target = new File(out, file.getName)
          finalImage.output(WebpWriter.MAX_LOSSLESS_COMPRESSION, target)
          Some(target)

        } catch {
          case _: Throwable => None
        }
      }
    }

    override def inspect(label: String)(f: ImmutableImage => Unit): Pipe[In,Out] =
      step(
        TransformationStep(
          s"inspect: $label",
          img => {
            f(img)
            img
          }
        )
      )

    def dryRun(): Unit = {
      val in = inputDir.getOrElse(sys.error("Input dir not set"))
      val files = ImageTransforms.listImages(in)

      println(s"Pipe Dry run")
      println(s"Input images : ${files.size}")
      println(s"# Steps        : ${steps.size}")

      steps.zipWithIndex.foreach {
        case (step, i) =>
          println(s"  ${i + 1}. ${step.name}")
      }
    }

  }
  final case class BatchContext(
                                 inputDir: File,
                                 outputDir: File
                               )

  final case class BatchStep(
                              name: String,
                              run: (BatchContext, Seq[File]) => Seq[BatchResult]
                            )
  type FilesBatch = Seq[File]
  sealed trait BatchResult
  case class BatchSuccess(file: File) extends BatchResult
  case class BatchFailure(file: File, reason: Throwable) extends BatchResult
  object Batch {
    def apply(): Batch[InMissing, OutMissing] =
      new Batch(None, None, Seq.empty)
  }

  final class Batch[In <: InState, Out <: OutState] private (
                              inputDir: Option[File],
                              outputDir: Option[File],
                              steps: Seq[BatchStep] = Nil
                            ) extends ObservableOp[FilesBatch, Batch[In,Out]]  {

    private[pipes] def stepCount: Int = steps.size
    def from(path: String) (implicit ev: In =:= InMissing): Batch[InPresent, Out] =
      new Batch(Some(new File(path)), outputDir, steps)

    def to(path: String)(implicit ev: Out =:= OutMissing): Batch[In, OutPresent] =
      new Batch(inputDir, Some(new File(path)), steps)

    def step(step: BatchStep): Batch[In, Out] =
      new Batch(inputDir, outputDir, steps :+ step)

    def run()(implicit ev1: In =:= InPresent, ev2: Out =:= OutPresent):  Seq[BatchResult] = {
      val in  = inputDir.getOrElse(sys.error("Input dir not set"))
      val out = outputDir.getOrElse(sys.error("Output dir not set"))

      val ctx = BatchContext(in, out)
      val files = ImageTransforms.listImages(in)

      steps.foldLeft(files -> Seq.empty[BatchResult]) {
        case ((currentFiles, _), step) =>
          val results = step.run(ctx, currentFiles)
          val nextFiles =
            results.collect { case BatchSuccess(f) => f }
          nextFiles -> results
      }._2
    }

    override def inspect(label: String)(f: FilesBatch => Unit): Batch[In, Out] =
      step(
        BatchStep(
          s"inspect: $label",
          (ctx,files) => {
            f(files)
            files.map(BatchSuccess)
          }
        )
      )


    override def dryRun(): Unit = {
      val in = inputDir.getOrElse(sys.error("Input dir not set"))
      val files = ImageTransforms.listImages(in)

      println(s"Batch Dry run")
      println(s"Input files : ${files.size}")
      println(s"# Steps       : ${steps.size}")

      steps.zipWithIndex.foreach {
        case (step, i) =>
          println(s"  ${i + 1}. ${step.name}")
      }
    }

    def foreach(
                 name: String
               )(f: (File, File, File) => TransformationResult): Batch[In, Out] =
      step(
        BatchStep(
          name,
          (ctx, files) => files.map(file => {
            toBatchResult(file)(f(file, ctx.inputDir, ctx.outputDir))
          })
        )
      )

    private def toBatchResult(
                               input: File
                             )(res: ImageTransforms.TransformationResult): BatchResult =
      res match {
        case Right(out) =>
          BatchSuccess(out)

        case Left(err) =>
          BatchFailure(input, new RuntimeException(err.message))
      }


  }
  implicit class TransformationResultOps(
                                          val res: ImageTransforms.TransformationResult
                                        ) extends AnyVal {
    def asBatch(input: File): BatchResult =
      res match {
        case Right(out) => BatchSuccess(out)
        case Left(err)  => BatchFailure(input, new RuntimeException(err.message))
      }
  }

}
