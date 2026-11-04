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