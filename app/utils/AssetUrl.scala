package utils

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.collection.concurrent.TrieMap

object AssetUrl {
  private val versions = TrieMap.empty[String, String]
  private val processVersion = java.lang.Long.toUnsignedString(System.currentTimeMillis(), 36)
  private val publicDirectory = Paths.get("public").toAbsolutePath.normalize()

  def versioned(path: String): String = {
    val url = controllers.routes.Assets.at(path).url
    val version = versions.getOrElseUpdate(path, contentVersion(path))
    s"$url?v=$version"
  }

  private def contentVersion(path: String): String = {
    val asset = publicDirectory.resolve(path).normalize()
    if (asset.startsWith(publicDirectory) && Files.isRegularFile(asset)) {
      sha256(asset).take(16)
    } else {
      // A process-specific value still invalidates caches if an asset is not on disk.
      processVersion
    }
  }

  private def sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      Iterator
        .continually(input.read(buffer))
        .takeWhile(_ != -1)
        .foreach(read => digest.update(buffer, 0, read))
    } finally input.close()
    digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
  }
}
