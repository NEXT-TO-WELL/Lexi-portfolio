package filters

import java.net.InetAddress
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.RequestHeader
import scala.util.Try

@Singleton
class ClientIpResolver @Inject() (configuration: Configuration) {
  private val trustedNetworks = {
    val configured = configuration.getOptional[Seq[String]]("security.trusted-proxies").toSeq.flatten
    val fromEnvironment = configuration
      .getOptional[String]("security.trusted-proxies-from-environment")
      .toSeq
      .flatMap(_.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)

    (configured ++ fromEnvironment).flatMap(ClientIpResolver.parseNetwork)
  }

  def resolve(request: RequestHeader): String =
    ClientIpResolver.resolve(request.remoteAddress, request.headers.get("X-Forwarded-For"), trustedNetworks)

  def canonicalize(address: String): Option[String] = ClientIpResolver.parseAddress(address).map(_.getHostAddress)
}

private[filters] object ClientIpResolver {
  final case class Network(address: Array[Byte], prefixLength: Int) {
    def contains(candidate: InetAddress): Boolean = {
      val bytes = candidate.getAddress
      if (bytes.length != address.length) false
      else {
        val fullBytes = prefixLength / 8
        val remainingBits = prefixLength % 8
        val fullBytesMatch = address.take(fullBytes).sameElements(bytes.take(fullBytes))
        val partialByteMatches = remainingBits == 0 || {
          val mask = (0xff << (8 - remainingBits)) & 0xff
          (address(fullBytes) & mask) == (bytes(fullBytes) & mask)
        }
        fullBytesMatch && partialByteMatches
      }
    }
  }

  def resolve(remoteAddress: String, forwardedFor: Option[String], trustedNetworks: Seq[Network]): String = {
    val remote = parseAddress(remoteAddress)
    remote match {
      case None                                                          => remoteAddress
      case Some(address) if !trustedNetworks.exists(_.contains(address)) => address.getHostAddress
      case Some(address)                                                 =>
        val chain = forwardedFor.toSeq
          .flatMap(_.split(","))
          .map(_.trim)
          .flatMap(parseAddress) :+ address

        chain.reverse
          .dropWhile(candidate => trustedNetworks.exists(_.contains(candidate)))
          .headOption
          .getOrElse(address)
          .getHostAddress
    }
  }

  def parseNetwork(value: String): Option[Network] = {
    val parts = value.split("/", -1)
    if (parts.length > 2) None
    else {
      parseAddress(parts.head).flatMap { address =>
        val maxPrefix = address.getAddress.length * 8
        val prefix = if (parts.length == 1) Some(maxPrefix) else parts(1).toIntOption
        prefix.filter(value => value >= 0 && value <= maxPrefix).map(Network(address.getAddress, _))
      }
    }
  }

  def parseAddress(value: String): Option[InetAddress] = {
    val raw = value.trim.stripPrefix("[").stripSuffix("]")
    val isIpv4 = raw.split("\\.", -1).toSeq match {
      case parts if parts.length == 4 =>
        parts.forall(part =>
          part.nonEmpty && part.forall(_.isDigit) && part.toIntOption.exists(n => n >= 0 && n <= 255)
        )
      case _ => false
    }
    val isIpv6 = raw.contains(":") && raw.forall(ch => ch.isDigit || "abcdefABCDEF:.".contains(ch))

    if (!isIpv4 && !isIpv6) None
    else Try(InetAddress.getByName(raw)).toOption
  }
}
