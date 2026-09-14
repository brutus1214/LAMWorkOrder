const $ = (id) => document.getElementById(id);

const statusRank = {
  New: 0,
  Scheduled: 1,
  InProgress: 2,
  Blocked: 3,
  Completed: 4,
  Cancelled: 5,
};
const statuses = ["New", "Scheduled", "InProgress", "Blocked", "Completed", "Cancelled"];
const priorities = ["Low", "Normal", "High", "Emergency"];
const assignableRoleOrder = ["Admin", "Manager", "Employee", "Security", "Technician"];
const employeeScopedRoles = ["Employee", "Security"];
const allMaxAgeMs = 30 * 24 * 60 * 60 * 1000;

let token = sessionStorage.getItem("lamworkorder.token");
let user = null;
let orders = [];
let assignees = [];
let filter = "New";
let attachmentObjectUrls = [];
let attachmentPreviewGeneration = 0;

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

function currentUserAssigneeNames() {
  if (!user) return new Set();
  const displayName = normalizedName(user.displayName);
  return new Set([
    displayName,
    normalizedName(user.username),
    normalizedName(assigneeOptionLabel(user)),
  ]);
}

function workOrderAssignedToCurrentUser(order) {
  if (!user) return false;
  const field = normalizedName(order.assignedTo);
  if (!field) return false;
  return (
    currentUserAssigneeNames().has(field) ||
    withoutStoreLabel(field) === normalizedName(user.displayName)
  );
}

function workOrderCreatedByCurrentUser(order) {
  if (!user) return false;
  return (
    order.createdById === user.id ||
    (!order.createdById && normalizedName(order.requestedBy) === normalizedName(user.displayName))
  );
}

function canUpdateWorkOrder(order) {
  if (!user) return false;
  if (user.role === "Admin") return true;
  if (user.role === "Manager") return user.storeNumber === order.storeNumber;
  if (employeeScopedRoles.includes(user.role)) {
    return workOrderCreatedByCurrentUser(order) || workOrderAssignedToCurrentUser(order);
  }
  if (user.role === "Technician") return workOrderAssignedToCurrentUser(order);
  return false;
}

function matchesCurrentUser(order) {
  return workOrderCreatedByCurrentUser(order) || workOrderAssignedToCurrentUser(order);
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
  const stores =
    user.role === "Admin" ? Array.from({ length: 50 }, (_, index) => index + 1) : [user.storeNumber];
  $("store").innerHTML = stores
    .map((storeNumber) => `<option value="${storeNumber}">LA Mart ${storeNumber}</option>`)
    .join("");
  $("store").value = user.role === "Admin" && user.storeNumber === 99 ? "1" : String(user.storeNumber);
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

function optionTag(value, text, selected = "") {
  const selectedAttribute = normalizedName(value) === normalizedName(selected) ? " selected" : "";
  return `<option value="${escapeHtml(value)}"${selectedAttribute}>${escapeHtml(text)}</option>`;
}

function optionsHtml(options, selected, displayValue = label) {
  return options.map((option) => optionTag(option, displayValue(option), selected)).join("");
}

function assigneeOptionsHtml(selected = "") {
  const knownValues = new Set([""]);
  const groups = assignableRoleOrder
    .map((role) => {
      const members = assignees.filter((account) => account.role === role);
      if (!members.length) return "";
      return `<optgroup label="${role}">${members
        .map((account) => {
          const optionLabel = assigneeOptionLabel(account);
          knownValues.add(normalizedName(optionLabel));
          return optionTag(optionLabel, optionLabel, selected);
        })
        .join("")}</optgroup>`;
    })
    .join("");
  const currentOption =
    selected && !knownValues.has(normalizedName(selected))
      ? optionTag(selected, selected, selected)
      : "";
  return optionTag("", "Unassigned", selected) + currentOption + groups;
}

function renderAssignees() {
  const select = $("assigned-to");
  if (!select) return;
  select.innerHTML = assigneeOptionsHtml();
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

function revokeAttachmentObjectUrls() {
  attachmentPreviewGeneration += 1;
  attachmentObjectUrls.forEach((url) => URL.revokeObjectURL(url));
  attachmentObjectUrls = [];
}

function renderAttachments(attachments = []) {
  if (!attachments.length) return '<p class="detail-muted">No photos or videos attached.</p>';
  return `<div class="attachment-list">${attachments
    .map(
      (attachment) => `
        <article class="attachment-card">
          <div
            class="attachment-preview"
            data-attachment-url="${escapeHtml(attachment.url)}"
            data-attachment-name="${escapeHtml(attachment.originalName)}"
            data-attachment-type="${escapeHtml(attachment.contentType)}"
          >Loading preview...</div>
          <div class="attachment-meta">
            <strong>${escapeHtml(attachment.originalName)}</strong>
            <small>${escapeHtml(label(attachment.contentType))}</small>
          </div>
        </article>`,
    )
    .join("")}</div>`;
}

async function hydrateAttachmentPreviews(generation) {
  const previews = document.querySelectorAll("#work-order-detail .attachment-preview");
  await Promise.all(
    [...previews].map(async (preview) => {
      const sourceUrl = preview.dataset.attachmentUrl;
      const contentType = preview.dataset.attachmentType || "";
      const originalName = preview.dataset.attachmentName || "Attachment";
      try {
        const headers = new Headers();
        if (token) headers.set("Authorization", `Bearer ${token}`);
        const response = await fetch(sourceUrl, { headers });
        if (!response.ok) throw new Error("Unable to load preview.");
        const blob = await response.blob();
        if (generation !== attachmentPreviewGeneration) return;
        const objectUrl = URL.createObjectURL(blob);
        attachmentObjectUrls.push(objectUrl);
        preview.textContent = "";
        if (contentType.startsWith("image/")) {
          const link = document.createElement("a");
          link.href = objectUrl;
          link.target = "_blank";
          link.rel = "noopener";
          const image = document.createElement("img");
          image.src = objectUrl;
          image.alt = originalName;
          link.append(image);
          preview.append(link);
        } else if (contentType.startsWith("video/")) {
          const video = document.createElement("video");
          video.src = objectUrl;
          video.controls = true;
          video.preload = "metadata";
          preview.append(video);
        } else {
          const link = document.createElement("a");
          link.href = objectUrl;
          link.target = "_blank";
          link.rel = "noopener";
          link.textContent = "Open attachment";
          preview.append(link);
        }
      } catch {
        if (generation === attachmentPreviewGeneration) {
          preview.textContent = "Preview unavailable. Sign in again or refresh.";
          preview.classList.add("attachment-preview-error");
        }
      }
    }),
  );
}

function renderWorkOrderDetail(order) {
  revokeAttachmentObjectUrls();
  const generation = attachmentPreviewGeneration;
  const canEdit = canUpdateWorkOrder(order);
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
      !canEdit && order.statusNote
        ? `<section class="detail-section"><h3>Status note</h3><p>${escapeHtml(order.statusNote)}</p></section>`
        : ""
    }
    ${
      canEdit
        ? `<form class="detail-section detail-edit-form" data-work-order-update data-order-id="${escapeHtml(order.id)}">
            <h3>Update work order</h3>
            <div class="detail-form-grid">
              <label>Title<input name="title" value="${escapeHtml(order.title)}" required maxlength="120"></label>
              <label>Priority<select name="priority">${optionsHtml(priorities, order.priority)}</select></label>
              <label class="span-2">Description<textarea name="description" required maxlength="2000">${escapeHtml(order.description)}</textarea></label>
              <label>Location<input name="location" value="${escapeHtml(order.location)}" required maxlength="160"></label>
              <label>Assigned to<select name="assignedTo">${assigneeOptionsHtml(order.assignedTo || "")}</select></label>
              <label>Status<select name="status">${optionsHtml(statuses, order.status)}</select></label>
              <label class="span-2">Status note<textarea name="statusNote" maxlength="1000">${escapeHtml(order.statusNote || "")}</textarea></label>
              <label class="span-2">Add pictures or videos<input name="attachments" type="file" accept="image/*,video/*" multiple></label>
            </div>
            <div class="detail-save-row"><button type="submit">Save work order</button></div>
          </form>`
        : ""
    }
    <section class="detail-section">
      <h3>Photos and videos</h3>
      ${renderAttachments(order.attachments)}
    </section>`;
  hydrateAttachmentPreviews(generation);
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
  revokeAttachmentObjectUrls();
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

$("work-order-detail").addEventListener("submit", async (event) => {
  const form = event.target.closest("[data-work-order-update]");
  if (!form) return;
  event.preventDefault();
  const button = form.querySelector("button[type=submit]");
  const orderId = form.dataset.orderId;
  const existing = orders.find((order) => order.id === orderId);
  button.disabled = true;
  $("work-order-error").textContent = "";
  try {
    const data = new FormData(form);
    const files = data.getAll("attachments").filter((file) => file.size);
    const updated = await api(`/api/work-orders/${encodeURIComponent(orderId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        storeNumber: existing?.storeNumber || user.storeNumber,
        title: String(data.get("title") || "").trim(),
        description: String(data.get("description") || "").trim(),
        requestedBy: existing?.requestedBy || user.displayName,
        location: String(data.get("location") || "").trim(),
        priority: data.get("priority"),
        assignedTo: data.get("assignedTo") || null,
        dueAt: existing?.dueAt || null,
        status: data.get("status"),
        statusNote: String(data.get("statusNote") || "").trim() || null,
      }),
    });
    if (files.length) {
      const media = new FormData();
      files.forEach((file) => media.append("files", file));
      await api(`/api/work-orders/${encodeURIComponent(updated.id)}/attachments`, {
        method: "POST",
        body: media,
      });
    }
    const refreshed = await api(`/api/work-orders/${encodeURIComponent(updated.id)}`);
    orders = orders.map((order) => (order.id === refreshed.id ? refreshed : order));
    render();
    renderWorkOrderDetail(refreshed);
    $("message").textContent = `${refreshed.workOrderNumber} saved.`;
  } catch (error) {
    $("work-order-error").textContent = error.message;
  } finally {
    button.disabled = false;
  }
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
