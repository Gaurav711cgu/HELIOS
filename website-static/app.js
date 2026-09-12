const output = document.querySelector("#api-output");
const backendUrl = document.querySelector("#backend-url");
const tenantId = document.querySelector("#tenant-id");
const workflowName = document.querySelector("#workflow-name");
const workflowDescription = document.querySelector("#workflow-description");
const synthesisForm = document.querySelector("#synthesis-form");
const registerButton = document.querySelector("#register-button");
const triggerButton = document.querySelector("#trigger-button");
const clearOutput = document.querySelector("#clear-output");

let synthesizedDefinition = null;
let latestWorkflowId = null;

function api(path) {
  return `${backendUrl.value.replace(/\/$/, "")}${path}`;
}

function render(value) {
  output.textContent = JSON.stringify(value, null, 2);
}

async function request(path, options = {}) {
  const response = await fetch(api(path), {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok) {
    throw new Error(typeof body === "string" ? body : JSON.stringify(body));
  }
  return body;
}

synthesisForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const result = await request("/api/v1/workflows/synthesize", {
      method: "POST",
      body: JSON.stringify({
        tenantId: tenantId.value,
        workflowName: workflowName.value,
        naturalLanguage: workflowDescription.value,
      }),
    });
    synthesizedDefinition = result.definition;
    render(result);
  } catch (error) {
    render({ error: error.message });
  }
});

registerButton.addEventListener("click", async () => {
  try {
    if (!synthesizedDefinition) {
      synthesisForm.requestSubmit();
      return;
    }
    const result = await request("/api/v1/workflows/definitions", {
      method: "POST",
      body: JSON.stringify(synthesizedDefinition),
    });
    render(result);
  } catch (error) {
    render({ error: error.message });
  }
});

triggerButton.addEventListener("click", async () => {
  try {
    const definitionName = synthesizedDefinition?.name || workflowName.value;
    const result = await request("/api/v1/workflows/trigger", {
      method: "POST",
      body: JSON.stringify({ definitionName }),
    });
    latestWorkflowId = result.id;
    render(result);
  } catch (error) {
    render({ error: error.message, latestWorkflowId });
  }
});

clearOutput.addEventListener("click", () => {
  synthesizedDefinition = null;
  latestWorkflowId = null;
  render({ status: "cleared" });
});
