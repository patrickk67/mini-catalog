let adminProducts = [];
document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("product-form");
  if (!form) return;
  form.addEventListener("submit", saveProduct);
  document.getElementById("cancel-edit").addEventListener("click", resetForm);
  document.getElementById("refresh-admin").addEventListener("click", loadAdmin);
  document.getElementById("admin-list").addEventListener("click", handleListAction);
  loadAdmin();
});
async function loadAdmin() {
  const list = document.getElementById("admin-list");
  list.innerHTML = `<div class="loading-state">Loading products…</div>`;
  try {
    adminProducts = await apiRequest("/products");
    document.getElementById("admin-count").textContent = `${adminProducts.length} products in your catalog`;
    list.innerHTML = adminProducts.map(p=>`<article class="admin-item"><img src="${escapeHtml(p.image)}" alt="" onerror="fallbackImage(this)"><div><h3>${escapeHtml(p.name)}</h3><p>${escapeHtml(p.category)} · ${p.stock} in stock</p><p class="admin-price">${money(p.price)}</p></div><div class="admin-item-actions"><button data-edit="${p.id}">Edit</button><button class="delete" data-delete="${p.id}">Delete</button></div></article>`).join("") || `<p class="muted">No products yet. Add your first product.</p>`;
  } catch(e) { list.innerHTML=`<p class="muted">Could not load products: ${escapeHtml(e.message)}</p>`; }
}
async function saveProduct(e) {
  e.preventDefault();
  const id = document.getElementById("product-id").value;
  const payload = {
    name: document.getElementById("name").value.trim(),
    description: document.getElementById("description").value.trim(),
    price: Number(document.getElementById("price").value),
    stock: Number(document.getElementById("stock").value),
    category: document.getElementById("category").value.trim(),
    rating: Number(document.getElementById("rating").value || 4.5),
    image: document.getElementById("image").value.trim()
  };
  if (!payload.name || !payload.description || !payload.category || !Number.isFinite(payload.price) || payload.price < 0 || !Number.isInteger(payload.stock) || payload.stock < 0 || payload.rating < 0 || payload.rating > 5) {
    toast("Please check all product fields and try again.", "error"); return;
  }
  const button=document.getElementById("save-button");button.disabled=true;button.textContent="Saving…";
  try {
    await apiRequest(id ? `/products/${id}` : "/products", { method:id?"PUT":"POST", body:JSON.stringify(payload) });
    toast(id ? "Product updated." : "Product added."); resetForm(); await loadAdmin();
  } catch(err) { toast(err.message,"error"); }
  finally {button.disabled=false;button.textContent="Save product";}
}
function handleListAction(e) {
  const edit=e.target.closest("[data-edit]"), del=e.target.closest("[data-delete]");
  if(edit) {
    const p=adminProducts.find(x=>x.id===Number(edit.dataset.edit));if(!p)return;
    document.getElementById("product-id").value=p.id;
    for(const key of ["name","description","price","stock","category","rating","image"]) document.getElementById(key).value=p[key] ?? "";
    document.getElementById("form-heading").textContent="Edit product";
    document.getElementById("save-button").textContent="Update product";
    document.getElementById("cancel-edit").classList.remove("hidden");
    document.getElementById("product-form").scrollIntoView({behavior:"smooth",block:"start"});
  }
  if(del) deleteProduct(Number(del.dataset.delete));
}
async function deleteProduct(id) {
  const p=adminProducts.find(x=>x.id===id);if(!p)return;
  if(!confirm(`Delete "${p.name}"? This cannot be undone.`))return;
  try { await apiRequest(`/products/${id}`,{method:"DELETE"}); toast("Product deleted."); await loadAdmin(); }
  catch(e){toast(e.message,"error");}
}
function resetForm() {
  document.getElementById("product-form").reset();document.getElementById("product-id").value="";
  document.getElementById("form-heading").textContent="Add a product";
  document.getElementById("save-button").textContent="Save product";
  document.getElementById("cancel-edit").classList.add("hidden");
  document.getElementById("rating").value="4.5";
}
