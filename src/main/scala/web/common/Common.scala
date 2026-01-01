package web.common

import java.nio.file.Paths
import java.time.{Instant, LocalDate}
import java.time.format.DateTimeFormatter

object Common {

  def getNameAndExtension(fullPath: String): (String, Option[String]) = {
    (getFileNameWithoutExtension(fullPath),getFileExtension(fullPath))
  }

  private def getFileExtension(fullPath: String): Option[String] = {
    val fileName = Paths.get(fullPath).getFileName.toString
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex != -1 && lastDotIndex < fileName.length - 1) {
      Some(fileName.substring(lastDotIndex + 1))
    } else {
      None
    }
  }

  private def getFileNameWithoutExtension(fileName: String): String = {
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0) fileName.substring(0, lastDotIndex)
    else {
      fileName
    }
  }

  def timestamp : String =  {
    Instant.now().toEpochMilli.toString
  }

  def today: String = {
    val date = LocalDate.now
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    date.format(formatter)
  }

}