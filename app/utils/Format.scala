package utils

object Format {
  def formatDate(timestamp: Long): String = {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    java.time.format.DateTimeFormatter
      .ofPattern("yyyy/MM/dd")
      .withZone(java.time.ZoneId.systemDefault())
      .format(instant)
  }

}
