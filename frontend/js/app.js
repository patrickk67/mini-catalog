const API_BASE = "/api";
const CART_KEY = "littleMarketCart";

function money(value) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value) || 0);
}
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, ch => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[ch]));
}
function fallbackImage(img) {
  img.onerror = null;
  img.src = "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600"><rect width="100%" height="100%" fill="#e9e9df"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="Arial" font-size="26" fill="#788170">little market</text></svg>`);
}
function getCart() {
  try { const v = JSON.parse(localStorage.getItem(CART_KEY) || "[]"); return Array.isArray(v) ? v : []; }
  catch { return []; }
}
function saveCart(cart) {
  localStorage.setItem(CART_KEY, JSON.stringify(cart));
  updateCartCount();
}
function updateCartCount() {
  const count = getCart().reduce((sum, item) => sum + Math.max(0, Number(item.quantity) || 0), 0);
  document.querySelectorAll("#cart-count").forEach(el => el.textContent = count);
}
function toast(message, type = "success") {
  let region = document.getElementById("toast-region");
  if (!region) {
    region = document.createElement("div"); region.id = "toast-region"; region.className = "toast-region"; region.setAttribute("aria-live","polite"); document.body.appendChild(region);
  }
  const el = document.createElement("div"); el.className = "toast" + (type === "error" ? " error" : ""); el.textContent = message;
  region.appendChild(el); setTimeout(() => el.remove(), 3000);
}
async function apiRequest(path, options = {}) {
  const response = await fetch(API_BASE + path, {
    ...options,
    headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), ...(options.headers || {}) }
  });
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status})`);
  return data;
}
async function addToCart(id) {
  try {
    const product = await apiRequest(`/products/${id}`);
    if (product.stock < 1) { toast("Sorry, this item is out of stock.", "error"); return; }
    const cart = getCart();
    const existing = cart.find(item => item.id === product.id);
    if (existing) {
      if (existing.quantity >= product.stock) { toast("You've reached the available stock limit.", "error"); return; }
      existing.quantity++;
    } else cart.push({ id: product.id, quantity: 1 });
    saveCart(cart); toast(`${product.name} added to your bag.`);
  } catch (e) { toast(e.message || "Could not add this item.", "error"); }
}
document.addEventListener("DOMContentLoaded", updateCartCount);
