const $ = (id) => document.getElementById(id);

const statusRank = {
  New: 0,
  Scheduled: 1,
  InProgress: 2,
  Blocked: 3,
  Completed: 4,
  Cancelled: 5,
};
const assignableRoleOrder = ["Employee", "Manager", "Technician"];
const allMaxAgeMs = 30 * 24 * 60 * 60 * 1000;

let token = sessionStorage.getItem("lamworkorder.token");
let user = null;
let orders = [];
let assignees = [];
let filter = "New";

const label = (value) => String(value || "").replace(/([a-z])([A-Z])/g, "$1 $2");
const escapeHtml = (value = "") =>
  String(value).replace(
    /[&<>'"]/g,
    (char) =>
      ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "'": "&#39;",
        '"': "&quot;",
      })[char],
  );
const assigneeStoreLabel = (account) =>
  account.storeNumber === 99 ? "All Stores" : `LA Mart ${account.storeNumber}`;
const assigneeOptionLabel = (account) => `${account.displayName} - ${assigneeStoreLabel(account)}`;
const setMobileIntakeOpen = (open) => $("create-form").classList.toggle("mobile-open", open);
const setViewHidden = (id, hidden) => {
  const element = $(id);
  element.hidden = hidden;
  element.style.display = hidden ? "none" : "";
};
const normalizedName = (value = "") =>
  String(value || "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, " ");
const withoutStoreLabel = (value) => value.replace(/\s+-\s+(la mart \d+|all stores)$/, "");

function matchesCurrentUser(order) {
  if (!user) return false;
  const displayName = normalizedName(user.displayName);
  const names = new Set([
    displayName,
    normalizedName(user.username),
    normalizedName(assigneeOptionLabel(user)),
  ]);
  return [order.requestedBy, order.assignedTo].some((value) => {
    const field = normalizedName(value);
    return names.has(field) || withoutStoreLabel(field) === displayName;
  });
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    signOut(false);
    throw new Error("Please sign in again.");
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      message = (await response.json()).detail || message;
    } catch {}
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

function showApp() {
  setViewHidden("login-view", true);
  setViewHidden("app-view", false);
  $("account-name").textContent = `${user.displayName} - ${user.role}`;
  $("requested-by").value = user.displayName;
  $("manage-users").hidden = !["Admin", "Manager"].includes(user.role);
  $("store").innerHTML = Array.from(
    { length: 50 },
    (_, index) => `<option value="${index + 1}">LA Mart ${index + 1}</option>`,
  ).join("");
  $("store").value = user.storeNumber === 99 ? "1" : String(user.storeNumber);
  load();
  loadAssignees();
}

function signOut(callApi = true) {
  if (callApi && token) api("/api/auth/logout", { method: "POST" }).catch(() => {});
  token = null;
  user = null;
  sessionStorage.removeItem("lamworkorder.token");
  closeWorkOrderDetail();
  setMobileIntakeOpen(false);
  setViewHidden("app-view", true);
  setViewHidden("login-view", false);
}

function parseCreatedAt(value) {
  if (!value) return NaN;
  return Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`);
}

function formatCreatedDate(value) {
  const created = parseCreatedAt(value);
  return Number.isNaN(created)
    ? String(value || "").split("T")[0]
    : new Intl.DateTimeFormat("en-US", {
        month: "numeric",
        day: "numeric",
        year: "2-digit",
      }).format(created);
}

function formatDateTime(value) {
  const created = parseCreatedAt(value);
  return Number.isNaN(created)
    ? String(value || "").replace("T", " ")
    : new Intl.DateTimeFormat("en-US", {
        month: "numeric",
        day: "numeric",
        year: "2-digit",
        hour: "numeric",
        minute: "2-digit",
      }).format(created);
}

function isRecentWorkOrder(order, now = Date.now()) {
  const created = parseCreatedAt(order.createdAt);
  return Number.isNaN(created) || created > now - allMaxAgeMs;
}

function visibleOrders() {
  const groups = {
    New: ["New"],
    Open: ["Scheduled", "InProgress", "Blocked"],
    Completed: ["Completed"],
    Closed: ["Cancelled"],
  };
  return orders
    .filter((order) =>
      filter === "Me"
        ? matchesCurrentUser(order)
        : filter === "All"
          ? isRecentWorkOrder(order)
          : groups[filter].includes(order.status),
    )
    .sort(
      (a, b) =>
        (statusRank[a.status] ?? 9) - (statusRank[b.status] ?? 9) ||
        b.createdAt.localeCompare(a.createdAt),
    );
}

function render() {
  const visible = visibleOrders();
  $("orders").innerHTML =
    visible
      .map(
        (order) => `
<tr data-order-id="${escapeHtml(order.id)}" role="button" tabindex="0" aria-label="Open ${escapeHtml(order.workOrderNumber)}">
  <td><span class="order-number"><strong>${escapeHtml(order.workOrderNumber)}</strong><small class="created-date">${escapeHtml(formatCreatedDate(order.createdAt))}</small></span></td>
  <td>${escapeHtml(order.title)}<small>Store ${order.storeNumber} - ${escapeHtml(order.location)}</small></td>
  <td><span class="pill ${escapeHtml(order.priority.toLowerCase())}">${escapeHtml(label(order.priority))}</span></td>
  <td><span class="pill ${escapeHtml(order.status.toLowerCase())}">${escapeHtml(label(order.status))}</span></td>
  <td>${escapeHtml(order.assignedTo || "Unassigned")}</td>
</tr>`,
      )
      .join("") || '<tr><td colspan="5" class="empty">No matching work orders.</td></tr>';
  $("total").textContent = orders.length;
  $("urgent").textContent = orders.filter((order) =>
    ["High", "Emergency"].includes(order.priority),
  ).length;
  $("progress").textContent = orders.filter((order) => order.status === "InProgress").length;
  $("message").textContent = `${visible.length} work order${visible.length === 1 ? "" : "s"}`;
}

async function load() {
  try {
    $("message").textContent = "Loading...";
    const query = $("search").value.trim();
    orders = await api(`/api/work-orders${query ? `?search=${encodeURIComponent(query)}` : ""}`);
    render();
  } catch (error) {
    $("message").textContent = error.message;
  }
}

function renderAssignees() {
  const select = $("assigned-to");
  if (!select) return;
  select.innerHTML =
    '<option value="">Unassigned</option>' +
    assignableRoleOrder
      .map((role) => {
        const members = assignees.filter((account) => account.role === role);
        return members.length
          ? `<optgroup label="${role}">${members
              .map((account) => {
                const optionLabel = assigneeOptionLabel(account);
                return `<option value="${escapeHtml(optionLabel)}">${escapeHtml(optionLabel)}</option>`;
              })
              .join("")}</optgroup>`
          : "";
      })
      .join("");
}

async function loadAssignees() {
  try {
    assignees = await api("/api/assignees");
    renderAssignees();
  } catch {}
}

function detailField(labelText, value) {
  return `<div class="detail-field"><span>${escapeHtml(labelText)}</span><strong>${escapeHtml(value || "Unassigned")}</strong></div>`;
}

function renderAttachments(attachments = []) {
  if (!attachments.length) return '<p class="detail-muted">No photos or videos attached.</p>';
  return `<div class="attachment-list">${attachments
    .map(
      (attachment) =>
        `<a href="${escapeHtml(attachment.url)}" target="_blank" rel="noopener">${escapeHtml(attachment.originalName)}<small>${escapeHtml(label(attachment.contentType))}</small></a>`,
    )
    .join("")}</div>`;
}

function renderWorkOrderDetail(order) {
  $("work-order-detail").innerHTML = `
    <div class="detail-title">
      <div>
        <strong>${escapeHtml(order.workOrderNumber)}</strong>
        <h2>${escapeHtml(order.title)}</h2>
      </div>
      <span class="pill ${escapeHtml(order.status.toLowerCase())}">${escapeHtml(label(order.status))}</span>
    </div>
    <div class="detail-grid">
      ${detailField("Store", `LA Mart ${order.storeNumber}`)}
      ${detailField("Priority", label(order.priority))}
      ${detailField("Assigned to", order.assignedTo || "Unassigned")}
      ${detailField("Requested by", order.requestedBy)}
      ${detailField("Location", order.location)}
      ${detailField("Created", formatDateTime(order.createdAt))}
    </div>
    <section class="detail-section">
      <h3>Description</h3>
      <p>${escapeHtml(order.description)}</p>
    </section>
    ${
      order.statusNote
        ? `<section class="detail-section"><h3>Status note</h3><p>${escapeHtml(order.statusNote)}</p></section>`
        : ""
    }
    <section class="detail-section">
      <h3>Photos and videos</h3>
      ${renderAttachments(order.attachments)}
    </section>`;
}

async function openWorkOrderDetail(orderId) {
  const existing = orders.find((order) => order.id === orderId);
  if (existing) renderWorkOrderDetail(existing);
  $("work-order-error").textContent = "";
  const dialog = $("work-order-dialog");
  dialog.hidden = false;
  if (dialog.showModal && !dialog.open) dialog.showModal();
  else dialog.hidden = false;
  try {
    const order = await api(`/api/work-orders/${encodeURIComponent(orderId)}`);
    orders = orders.map((item) => (item.id === order.id ? order : item));
    renderWorkOrderDetail(order);
  } catch (error) {
    $("work-order-error").textContent = error.message;
  }
}

function closeWorkOrderDetail() {
  const dialog = $("work-order-dialog");
  if (!dialog) return;
  if (dialog.open) dialog.close();
  dialog.hidden = true;
}

$("orders").addEventListener("click", (event) => {
  const row = event.target.closest("tr[data-order-id]");
  if (row) openWorkOrderDetail(row.dataset.orderId);
});

$("orders").addEventListener("keydown", (event) => {
  if (!["Enter", " "].includes(event.key)) return;
  const row = event.target.closest("tr[data-order-id]");
  if (!row) return;
  event.preventDefault();
  openWorkOrderDetail(row.dataset.orderId);
});

$("close-work-order").onclick = closeWorkOrderDetail;
$("work-order-dialog").addEventListener("click", (event) => {
  if (event.target === $("work-order-dialog")) closeWorkOrderDetail();
});
$("mobile-create-toggle").onclick = () => setMobileIntakeOpen(true);
$("close-create").onclick = () => setMobileIntakeOpen(false);

$("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("login-error").textContent = "";
  try {
    const result = await api("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(Object.fromEntries(new FormData(event.target))),
    });
    token = result.token;
    user = result.user;
    sessionStorage.setItem("lamworkorder.token", token);
    showApp();
  } catch (error) {
    $("login-error").textContent = error.message;
  }
});

$("create-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.target.querySelector("button[type=submit]");
  button.disabled = true;
  try {
    const data = new FormData(event.target);
    const files = data.getAll("attachments").filter((file) => file.size);
    const payload = Object.fromEntries(data.entries());
    delete payload.attachments;
    payload.storeNumber = Number(payload.storeNumber);
    payload.assignedTo = payload.assignedTo || null;
    payload.requestedBy = user.displayName;
    const created = await api("/api/work-orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (files.length) {
      const media = new FormData();
      files.forEach((file) => media.append("files", file));
      await api(`/api/work-orders/${created.id}/attachments`, { method: "POST", body: media });
    }
    event.target.reset();
    $("store").value = user.storeNumber === 99 ? "1" : String(user.storeNumber);
    $("requested-by").value = user.displayName;
    await load();
    setMobileIntakeOpen(false);
  } catch (error) {
    $("message").textContent = error.message;
  } finally {
    button.disabled = false;
  }
});

$("filters").addEventListener("click", (event) => {
  const value = event.target.dataset.filter;
  if (!value) return;
  filter = value;
  document
    .querySelectorAll("#filters button")
    .forEach((button) => button.classList.toggle("active", button === event.target));
  render();
});

$("refresh").onclick = load;
let timer;
$("search").oninput = () => {
  clearTimeout(timer);
  timer = setTimeout(load, 250);
};
$("menu-button").onclick = () => {
  const hidden = !$("account-menu").hidden;
  $("account-menu").hidden = hidden;
  $("menu-button").setAttribute("aria-expanded", String(!hidden));
};
$("logout-button").onclick = () => signOut();
$("profile-button").onclick = () => {
  $("account-menu").hidden = true;
  const form = $("profile-form");
  form.displayName.value = user.displayName;
  form.email.value = user.email || "";
  $("profile-meta").textContent = `${user.username} - ${user.role} - ${
    user.storeNumber === 99 ? "All Stores" : `LA Mart ${user.storeNumber}`
  }`;
  $("profile-dialog").showModal();
};
$("profile-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    user = await api("/api/profile", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        displayName: event.target.displayName.value,
        email: event.target.email.value || null,
      }),
    });
    $("profile-dialog").close();
    showApp();
  } catch (error) {
    $("profile-error").textContent = error.message;
  }
});

(async () => {
  if (!token) return;
  try {
    user = await api("/api/profile");
    showApp();
  } catch {
    signOut(false);
  }
})();
