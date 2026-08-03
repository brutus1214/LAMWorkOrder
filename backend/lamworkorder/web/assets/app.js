const statuses = ["New", "Scheduled", "InProgress", "Blocked", "Completed", "Cancelled"];
const $ = (id) => document.getElementById(id);
statuses.forEach((value) => $("status").add(new Option(value.replace(/([a-z])([A-Z])/g, "$1 $2"), value)));

function pill(value) {
  return `<span class="pill ${value.toLowerCase()}">${value.replace(/([a-z])([A-Z])/g, "$1 $2")}</span>`;
}

async function load() {
  const params = new URLSearchParams();
  if ($("status").value) params.set("status", $("status").value);
  if ($("search").value.trim()) params.set("search", $("search").value.trim());
  $("message").textContent = "Loading…";
  try {
    const response = await fetch(`/api/work-orders?${params}`);
    if (!response.ok) throw new Error(`API returned ${response.status}`);
    const orders = await response.json();
    $("orders").innerHTML = orders.map((order) => `<tr>
      <td><strong>${order.workOrderNumber}</strong></td>
      <td>${order.title}<small>${order.location}</small></td>
      <td>${pill(order.priority)}</td><td>${pill(order.status)}</td>
      <td>${order.assignedTo || "Unassigned"}</td></tr>`).join("") ||
      '<tr><td colspan="5" class="empty">No matching work orders.</td></tr>';
    $("total").textContent = orders.length;
    $("urgent").textContent = orders.filter((x) => ["High", "Emergency"].includes(x.priority)).length;
    $("progress").textContent = orders.filter((x) => x.status === "InProgress").length;
    $("message").textContent = `${orders.length} work order${orders.length === 1 ? "" : "s"}`;
  } catch (error) {
    $("message").textContent = `Unable to load: ${error.message}`;
  }
}

$("create-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  const payload = Object.fromEntries(form.entries());
  if (!payload.assignedTo) payload.assignedTo = null;
  const button = event.target.querySelector("button");
  button.disabled = true;
  try {
    const response = await fetch("/api/work-orders", {
      method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(payload)
    });
    if (!response.ok) throw new Error((await response.json()).detail || "Creation failed");
    event.target.reset();
    await load();
  } catch (error) {
    $("message").textContent = String(error.message || error);
  } finally {
    button.disabled = false;
  }
});
$("refresh").addEventListener("click", load);
$("status").addEventListener("change", load);
let timer;
$("search").addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(load, 250); });
load();

