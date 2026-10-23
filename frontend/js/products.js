let allProducts = [];
let activeCategory = "";
const grid = document.getElementById("product-grid");
const searchInput = document.getElementById("search-input");
const categoryFilter = document.getElementById("category-filter");
const sortSelect = document.getElementById("sort-select");
const chips = document.getElementById("category-chips");
const modal = document.getElementById("product-modal");

document.addEventListener("DOMContentLoaded", initCatalog);
async function initCatalog() {
  if (!grid) return;
  try {
    allProducts = await apiRequest("/products");
    populateCategories();
    renderProducts();
  } catch (e) {
    grid.innerHTML = `<div class="loading-state">We couldn't load the catalog. Make sure the Java server is running, then refresh this page.<br><br><small>${escapeHtml(e.message)}</small></div>`;
  }
  searchInput?.addEventListener("input", renderProducts);
  categoryFilter?.addEventListener("change", () => { activeCategory = categoryFilter.value; syncChips(); renderProducts(); });
  sortSelect?.addEventListener("change", renderProducts);
  document.getElementById("reset-filters")?.addEventListener("click", resetFilters);
  document.addEventListener("click", e => {
    if (e.target.closest("[data-close-modal]")) closeModal();
    const view = e.target.closest("[data-view-product]");
    if (view) showProduct(Number(view.dataset.viewProduct));
    const add = e.target.closest("[data-add-product]");
    if (add) addToCart(Number(add.dataset.addProduct));
    const chip = e.target.closest("[data-category-chip]");
    if (chip) { activeCategory = chip.dataset.categoryChip; categoryFilter.value = activeCategory; syncChips(); renderProducts(); }
  });
  document.addEventListener("keydown", e => { if (e.key === "Escape") closeModal(); if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") { e.preventDefault(); searchInput?.focus(); } });
}
function populateCategories() {
  const categories = [...new Set(allProducts.map(p => p.category))].sort();
  categoryFilter.innerHTML = `<option value="">All categories</option>` + categories.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join("");
  chips.innerHTML = [`<button class="chip active" data-category-chip="">Everything</button>`, ...categories.map(c => `<button class="chip" data-category-chip="${escapeHtml(c)}">${escapeHtml(c)}</button>`)].join("");
}
function syncChips() {
  chips?.querySelectorAll(".chip").forEach(b => b.classList.toggle("active", b.dataset.categoryChip === activeCategory));
}
function renderProducts() {
  if (!grid) return;
  const q = (searchInput?.value || "").trim().toLowerCase();
  let list = allProducts.filter(p => (!activeCategory || p.category === activeCategory) && (!q || `${p.name} ${p.description} ${p.category}`.toLowerCase().includes(q)));
  const sort = sortSelect?.value || "featured";
  if (sort === "price-asc") list.sort((a,b) => a.price-b.price);
  if (sort === "price-desc") list.sort((a,b) => b.price-a.price);
  if (sort === "name") list.sort((a,b) => a.name.localeCompare(b.name));
  document.getElementById("result-count").textContent = `${list.length} ${list.length === 1 ? "find" : "finds"}`;
  document.getElementById("empty-state").classList.toggle("hidden", list.length > 0);
  grid.classList.toggle("hidden", list.length === 0);
  grid.innerHTML = list.map(p => `
    <article class="product-card">
      <div class="product-image-wrap">
        <img class="product-image" src="${escapeHtml(p.image)}" alt="${escapeHtml(p.name)}" loading="lazy" onerror="fallbackImage(this)">
        ${p.stock <= 3 && p.stock > 0 ? `<span class="product-tag">Almost gone</span>` : p.stock === 0 ? `<span class="product-tag">Sold out</span>` : `<span class="product-tag">${escapeHtml(p.category)}</span>`}
        <button class="quick-view" data-view-product="${p.id}">Quick view ↗</button>
      </div>
      <div class="product-info-top"><div><h3 class="product-name">${escapeHtml(p.name)}</h3><span class="product-category">${escapeHtml(p.category)}</span></div><span class="product-rating">★ ${Number(p.rating).toFixed(1)}</span></div>
      <div class="product-bottom"><span class="product-price">${money(p.price)}</span><button class="add-button" data-add-product="${p.id}" aria-label="Add ${escapeHtml(p.name)} to bag" ${p.stock < 1 ? "disabled" : ""}>+</button></div>
      ${p.stock < 1 ? `<div class="stock-note">Currently out of stock</div>` : `<div class="stock-note">${p.stock <= 3 ? `Only ${p.stock} left` : "Ready for a new home"}</div>`}
    </article>`).join("");
}
function resetFilters() { searchInput.value = ""; activeCategory = ""; categoryFilter.value = ""; sortSelect.value = "featured"; syncChips(); renderProducts(); }
async function showProduct(id) {
  try {
    const p = await apiRequest(`/products/${id}`);
    document.getElementById("modal-content").innerHTML = `<div class="modal-product"><img src="${escapeHtml(p.image)}" alt="${escapeHtml(p.name)}" onerror="fallbackImage(this)"><div><span class="eyebrow">${escapeHtml(p.category)}</span><h2 id="modal-title">${escapeHtml(p.name)}</h2><div class="product-rating">★ ${Number(p.rating).toFixed(1)} / 5</div><p>${escapeHtml(p.description)}</p><div class="modal-price">${money(p.price)}</div><p>${p.stock > 0 ? `${p.stock} available` : "Currently out of stock"}</p><button class="button button-dark" data-add-product="${p.id}" ${p.stock < 1 ? "disabled" : ""}>Add to bag <span>＋</span></button></div></div>`;
    modal.classList.remove("hidden"); document.body.style.overflow = "hidden";
  } catch (e) { toast(e.message, "error"); }
}
function closeModal() { if (modal) modal.classList.add("hidden"); document.body.style.overflow = ""; }
