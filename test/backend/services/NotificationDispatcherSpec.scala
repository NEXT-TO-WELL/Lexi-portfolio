package services

import nl.martijndwars.webpush.{Encoding, Notification as WebPushNotification, PushService}
import org.apache.http.{HttpResponse, HttpVersion}
import org.apache.http.message.BasicHttpResponse
import org.apache.http.util.EntityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatestplus.play.PlaySpec

import java.nio.charset.StandardCharsets
import java.security.{KeyPair, KeyPairGenerator, Security}
import java.security.spec.ECGenParameterSpec

class NotificationDispatcherSpec extends PlaySpec {

  "Aes128GcmWebPushSender" should {
    "always select the RFC 8291 content encoding" in {
      ensureBouncyCastle()
      val pushService = new RecordingPushService
      val sender = new Aes128GcmWebPushSender(pushService)

      sender.send(testNotification("encoding selection test".getBytes(StandardCharsets.UTF_8)))

      pushService.encoding mustBe Some(Encoding.AES128GCM)
    }

    "produce an encrypted aes128gcm request body" in {
      ensureBouncyCastle()
      val payload = "Lexi encryption test".getBytes(StandardCharsets.UTF_8)
      val service = new PushService(generateKeyPair(), "mailto:test@example.com")
      val notification = testNotification(payload)

      val request = service.preparePost(notification, Encoding.AES128GCM)
      val encryptedBody = EntityUtils.toByteArray(request.getEntity)

      request.getFirstHeader("Content-Encoding").getValue mustBe "aes128gcm"
      encryptedBody.length must be > payload.length
      new String(encryptedBody, StandardCharsets.UTF_8) must not include "Lexi encryption test"
    }
  }

  private final class RecordingPushService extends PushService {
    var encoding: Option[Encoding] = None

    override def send(notification: WebPushNotification, selectedEncoding: Encoding): HttpResponse = {
      encoding = Some(selectedEncoding)
      new BasicHttpResponse(HttpVersion.HTTP_1_1, 201, "Created")
    }
  }

  private def ensureBouncyCastle(): Unit =
    if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider())

  private def generateKeyPair(): KeyPair = {
    val generator = KeyPairGenerator.getInstance("ECDH", "BC")
    generator.initialize(new ECGenParameterSpec("secp256r1"))
    generator.generateKeyPair()
  }

  private def testNotification(payload: Array[Byte]): WebPushNotification =
    new WebPushNotification(
      "https://push.example.test/subscriptions/1",
      generateKeyPair().getPublic,
      Array.tabulate[Byte](16)(_.toByte),
      payload
    )
}
