self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("push", (event) => {
  if (!event.data) return;

  let payload;
  try {
    payload = event.data.json();
  } catch (_) {
    return;
  }

  const notificationId = String(payload.notificationId || "");
  event.waitUntil(
    Promise.all([
      self.registration.showNotification(
        payload.title || "Lexiからのお知らせ",
        {
          body: payload.body || "新しいお知らせがあります。",
          icon: "/assets/images/icon-192.png",
          badge: "/assets/images/icon-192.png",
          tag: notificationId
            ? `lexi-notification-${notificationId}`
            : "lexi-notification",
          data: {
            notificationId,
            url: payload.url || "/home",
          },
        },
      ),
      self.clients
        .matchAll({ type: "window", includeUncontrolled: true })
        .then((clients) =>
          clients.forEach((client) =>
            client.postMessage({
              type: "LEXI_PUSH_RECEIVED",
              notificationId,
            }),
          ),
        ),
    ]),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  const data = event.notification.data || {};
  const target = new URL(data.url || "/home", self.location.origin);
  if (data.notificationId) {
    target.searchParams.set("pushNotificationId", data.notificationId);
  }

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clients) => {
      const sameOriginClient = clients.find((client) => new URL(client.url).origin === target.origin);
      if (sameOriginClient) {
        return sameOriginClient.navigate(target.href).then((client) => client && client.focus());
      }
      return self.clients.openWindow(target.href);
    }),
  );
});
