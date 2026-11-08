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