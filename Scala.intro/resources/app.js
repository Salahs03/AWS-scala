const button = document.getElementById("give-name");
const panel = document.getElementById("response");

button.addEventListener("click", async () => {
  const start = performance.now();
  const response = await fetch("/api/name");
  const data = await response.json();
  const durationMs = Math.round(performance.now() - start);
  const requestId = response.headers.get("X-Request-Id");

  panel.textContent = [
    data.name,
    `Status: ${response.status} ${response.statusText}`,
    `Time: ${durationMs} ms`,
    `Request ID: ${requestId}`,
  ].join("\n");
});
const serverButton = document.getElementById("which-server");
const serverPanel = document.getElementById("server-response");

serverButton.addEventListener("click", async () => {
  serverPanel.textContent = "Checking...";

  try {
    const response = await fetch("/api/whoami");

    if (!response.ok) {
      throw new Error(`Request failed: HTTP ${response.status}`);
    }

    const data = await response.json();

    serverPanel.textContent = [
      `Server: ${data.serverId}`,
      `Availability Zone: ${data.availabilityZone}`,
    ].join("\n");
  } catch (error) {
    serverPanel.textContent = `Could not get server identity: ${error.message}`;
  }
});
const messageInput = document.getElementById("message-text");
const sendButton = document.getElementById("send-message");
const readButton = document.getElementById("read-message");
const queuePanel = document.getElementById("queue-response");

async function makeQueueRequest(path, body) {
  sendButton.disabled = true;
  readButton.disabled = true;
  queuePanel.textContent = "Working...";

  const start = performance.now();

  try {
    const options = {
      method: "POST",
    };

    if (body !== undefined) {
      options.headers = {
        "Content-Type": "application/json",
      };
      options.body = JSON.stringify(body);
    }

    const response = await fetch(path, options);

    const result =
      response.status === 204
        ? "No message received."
        : await response.text();

    const durationMs = Math.round(performance.now() - start);
    const requestId = response.headers.get("X-Request-Id");

    queuePanel.textContent = [
      `Status: ${response.status} ${response.statusText}`,
      result,
      `Time: ${durationMs} ms`,
      `Request ID: ${requestId}`,
    ].join("\n");
  } catch (error) {
    queuePanel.textContent =
      `Could not complete the request: ${error.message}`;
  } finally {
    sendButton.disabled = false;
    readButton.disabled = false;
  }
}

sendButton.addEventListener("click", () => {
  makeQueueRequest("/api/messages", {
    text: messageInput.value,
  });
});

readButton.addEventListener("click", () => {
  makeQueueRequest("/api/messages/receive");
});
