package filters

import org.scalatestplus.play.PlaySpec

class ClientIpResolverSpec extends PlaySpec {
  private val trusted = Seq(ClientIpResolver.parseNetwork("10.0.0.0/8").get)

  "ClientIpResolver" should {
    "ignore forwarding headers from an untrusted direct peer" in {
      ClientIpResolver.resolve("198.51.100.20", Some("203.0.113.10"), trusted) mustBe "198.51.100.20"
    }

    "select the rightmost untrusted address behind trusted proxies" in {
      ClientIpResolver.resolve(
        "10.0.0.5",
        Some("203.0.113.10, 198.51.100.20, 10.0.0.6"),
        trusted
      ) mustBe "198.51.100.20"
    }

    "ignore a spoofed address prepended by the client" in {
      ClientIpResolver.resolve("10.0.0.5", Some("203.0.113.10, 198.51.100.20"), trusted) mustBe "198.51.100.20"
    }

    "reject hostnames without resolving them" in {
      ClientIpResolver.parseAddress("attacker.example") mustBe None
    }
  }
}
