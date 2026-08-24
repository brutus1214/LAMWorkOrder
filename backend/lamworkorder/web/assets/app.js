const $ = (id) => document.getElementById(id);
const statusRank = {New: 0, Scheduled: 1, InProgress: 2, Blocked: 3, Completed: 4, Cancelled: 5};
const assignableRoleOrder = ["Employee", "Manager", "Technician"];
const allMaxAgeMs = 30 * 24 * 60 * 60 * 1000;
let token = sessionStorage.getItem("lamworkorder.token"); let user = null; let orders = []; let assignees = []; let filter = "New";
const label = (value) => value.replace(/([a-z])([A-Z])/g, "$1 $2");
const escapeHtml = (value = "") => String(value).replace(/[&<>'"]/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"}[c]));
const assigneeStoreLabel = (user) => user.storeNumber === 99 ? "All Stores" : `LA Mart ${user.storeNumber}`;
const assigneeOptionLabel = (user) => `${user.displayName} - ${assigneeStoreLabel(user)}`;
const setMobileIntakeOpen = (open) => $("create-form").classList.toggle("mobile-open", open);
const normalizedName = (value = "") => String(value || "").trim().toLowerCase().replace(/\s+/g, " ");
const withoutStoreLabel = (value) => value.replace(/\s+-\s+(la mart \d+|all stores)$/, "");
function matchesCurrentUser(order) { if (!user) return false; const displayName = normalizedName(user.displayName); const names = new Set([displayName, normalizedName(user.username), normalizedName(assigneeOptionLabel(user))]); return [order.requestedBy, order.assignedTo].some(value => { const field = normalizedName(value); return names.has(field) || withoutStoreLabel(field) === displayName; }); }
async function api(path, options = {}) {
  const headers = new Headers(options.headers || {}); if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(path, {...options, headers});
  if (response.status === 401) { signOut(false); throw new Error("Please sign in again."); }
  if (!response.ok) { let message = `Request failed (${response.status})`; try { message = (await response.json()).detail || message; } catch {} throw new Error(message); }
  return response.status === 204 ? null : response.json();
}
function showApp() {
  $("login-view").hidden = true; $("app-view").hidden = false; $("account-name").textContent = `${user.displayName} · ${user.role}`;
  $("requested-by").value = user.displayName; $("manage-users").hidden = !["Admin", "Manager"].includes(user.role);
  $("store").innerHTML = Array.from({length: 50}, (_, i) => `<option value="${i + 1}">LA Mart ${i + 1}</option>`).join("");
  $("store").value = user.storeNumber === 99 ? "1" : String(user.storeNumber); load(); loadAssignees();
}
function signOut(callApi = true) { if (callApi && token) api("/api/auth/logout", {method:"POST"}).catch(()=>{}); token = null; user = null; sessionStorage.removeItem("lamworkorder.token"); $("app-view").hidden = true; $("login-view").hidden = false; }
function parseCreatedAt(value) { if (!value) return NaN; return Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`); }
function formatCreatedDate(value) { const created = parseCreatedAt(value); return Number.isNaN(created) ? String(value || "").split("T")[0] : new Intl.DateTimeFormat("en-US", {month:"numeric", day:"numeric", year:"2-digit"}).format(created); }
function isRecentWorkOrder(order, now = Date.now()) { const created = parseCreatedAt(order.createdAt); return Number.isNaN(created) || created > now - allMaxAgeMs; }
function visibleOrders() {
  const groups = {New:["New"], Open:["Scheduled","InProgress","Blocked"], Completed:["Completed"], Closed:["Cancelled"]};
  return orders.filter(x => filter === "Me" ? matchesCurrentUser(x) : filter === "All" ? isRecentWorkOrder(x) : groups[filter].includes(x.status)).sort((a,b) => (statusRank[a.status] ?? 9) - (statusRank[b.status] ?? 9) || b.createdAt.localeCompare(a.createdAt));
}
function render() {
  const visible = visibleOrders(); $("orders").innerHTML = visible.map(o => `<tr><td><span class="order-number"><strong>${escapeHtml(o.workOrderNumber)}</strong><small class="created-date">${escapeHtml(formatCreatedDate(o.createdAt))}</small></span></td><td>${escapeHtml(o.title)}<small>Store ${o.storeNumber} · ${escapeHtml(o.location)}</small></td><td><span class="pill ${o.priority.toLowerCase()}">${label(o.priority)}</span></td><td><span class="pill ${o.status.toLowerCase()}">${label(o.status)}</span></td><td>${escapeHtml(o.assignedTo || "Unassigned")}</td></tr>`).join("") || '<tr><td colspan="5" class="empty">No matching work orders.</td></tr>';
  $("total").textContent = orders.length; $("urgent").textContent = orders.filter(x => ["High","Emergency"].includes(x.priority)).length; $("progress").textContent = orders.filter(x => x.status === "InProgress").length; $("message").textContent = `${visible.length} work order${visible.length === 1 ? "" : "s"}`;
}
async function load() { try { $("message").textContent = "Loading…"; const q = $("search").value.trim(); orders = await api(`/api/work-orders${q ? `?search=${encodeURIComponent(q)}` : ""}`); render(); } catch(e) { $("message").textContent = e.message; } }
function renderAssignees(){ const select=$("assigned-to"); if(!select)return; select.innerHTML='<option value="">Unassigned</option>'+assignableRoleOrder.map(role=>{const members=assignees.filter(u=>u.role===role);return members.length?`<optgroup label="${role}">${members.map(u=>{const optionLabel=assigneeOptionLabel(u);return `<option value="${escapeHtml(optionLabel)}">${escapeHtml(optionLabel)}</option>`;}).join("")}</optgroup>`:"";}).join(""); }
async function loadAssignees(){ try{assignees=await api("/api/assignees"); renderAssignees();}catch{} }
$("mobile-create-toggle").onclick=()=>setMobileIntakeOpen(true); $("close-create").onclick=()=>setMobileIntakeOpen(false);
$("login-form").addEventListener("submit", async e => { e.preventDefault(); $("login-error").textContent = ""; try { const result = await api("/api/auth/login", {method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(Object.fromEntries(new FormData(e.target)))}); token=result.token; user=result.user; sessionStorage.setItem("lamworkorder.token",token); showApp(); } catch(err) { $("login-error").textContent=err.message; } });
$("create-form").addEventListener("submit", async e => { e.preventDefault(); const button=e.target.querySelector("button[type=submit]"); button.disabled=true; try { const data=new FormData(e.target); const files=data.getAll("attachments").filter(f=>f.size); const payload=Object.fromEntries(data.entries()); delete payload.attachments; payload.storeNumber=Number(payload.storeNumber); payload.assignedTo=payload.assignedTo||null; payload.requestedBy=user.displayName; const created=await api("/api/work-orders",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(payload)}); if(files.length){const media=new FormData();files.forEach(f=>media.append("files",f));await api(`/api/work-orders/${created.id}/attachments`,{method:"POST",body:media});} e.target.reset(); $("store").value=user.storeNumber===99?"1":String(user.storeNumber); $("requested-by").value=user.displayName; await load(); setMobileIntakeOpen(false); } catch(err){$("message").textContent=err.message;} finally{button.disabled=false;} });
$("filters").addEventListener("click", e => { const value=e.target.dataset.filter;if(!value)return;filter=value;document.querySelectorAll("#filters button").forEach(b=>b.classList.toggle("active",b===e.target));render(); });
$("refresh").onclick=load; let timer; $("search").oninput=()=>{clearTimeout(timer);timer=setTimeout(load,250);};
$("menu-button").onclick=()=>{const hidden=!$("account-menu").hidden;$("account-menu").hidden=hidden;$("menu-button").setAttribute("aria-expanded",String(!hidden));}; $("logout-button").onclick=()=>signOut();
$("profile-button").onclick=()=>{$("account-menu").hidden=true;const form=$("profile-form");form.displayName.value=user.displayName;form.email.value=user.email||"";$("profile-meta").textContent=`${user.username} · ${user.role} · ${user.storeNumber===99?"All Stores":`LA Mart ${user.storeNumber}`}`;$("profile-dialog").showModal();};
$("profile-form").addEventListener("submit",async e=>{e.preventDefault();try{user=await api("/api/profile",{method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify({displayName:e.target.displayName.value,email:e.target.email.value||null})});$("profile-dialog").close();showApp();}catch(err){$("profile-error").textContent=err.message;}});
(async()=>{if(!token)return;try{user=await api("/api/profile");showApp();}catch{signOut(false);}})();
