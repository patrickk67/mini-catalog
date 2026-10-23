let productMap = new Map();
document.addEventListener("DOMContentLoaded", renderCart);
async function renderCart() {
  const itemsEl = document.getElementById("cart-items"), summaryEl = document.getElementById("cart-summary");
  if (!itemsEl || !summaryEl) return;
  try {
    const products = await apiRequest("/products");
    productMap = new Map(products.map(p => [p.id, p]));
    let cart = getCart().filter(item => productMap.has(Number(item.id)));
    saveCart(cart);
    const lines = cart.map(item => ({ product: productMap.get(Number(item.id)), quantity: Math.max(1, Number(item.quantity) || 1) }));
    if (!lines.length) {
      itemsEl.innerHTML = `<div class="cart-empty"><div class="empty-icon">♧</div><h2>Your bag is taking a little nap.</h2><p>There’s nothing in it just yet. Go find something you love.</p><a class="button button-dark" href="/#catalog">Explore the collection ↗</a></div>`;
      summaryEl.innerHTML = ""; return;
    }
    itemsEl.innerHTML = lines.map(({product:p,quantity:q}) => `<article class="cart-row">
      <img src="${escapeHtml(p.image)}" alt="${escapeHtml(p.name)}" onerror="fallbackImage(this)">
      <div><h3>${escapeHtml(p.name)}</h3><div class="cart-category">${escapeHtml(p.category)}</div><div class="cart-unit-price">${money(p.price)} each</div>
      <div class="quantity-control"><button data-quantity="-1" data-id="${p.id}" aria-label="Decrease quantity">−</button><span>${q}</span><button data-quantity="1" data-id="${p.id}" aria-label="Increase quantity" ${q >= p.stock ? "disabled" : ""}>＋</button></div>
      <button class="remove-button" data-remove="${p.id}">Remove</button></div><div class="cart-line-total">${money(p.price*q)}</div></article>`).join("");
    const subtotal = lines.reduce((sum,l)=>sum+l.product.price*l.quantity,0);
    summaryEl.innerHTML = `<h2>Bag summary</h2><div class="summary-line"><span>Items (${lines.reduce((s,l)=>s+l.quantity,0)})</span><span>${money(subtotal)}</span></div><div class="summary-line"><span>Delivery</span><span>Free</span></div><div class="summary-line summary-total"><span>Total</span><span>${money(subtotal)}</span></div><button class="button button-dark" id="demo-checkout">Continue to checkout <span>↗</span></button><p class="summary-note">This is a student demo. Checkout and payments are not enabled. Your bag is saved in this browser.</p>`;
    summaryEl.querySelector("#demo-checkout").addEventListener("click",()=>toast("Checkout is a demo feature and is not enabled yet."));
    itemsEl.querySelectorAll("[data-quantity]").forEach(btn=>btn.addEventListener("click",()=>changeQuantity(Number(btn.dataset.id),Number(btn.dataset.quantity))));
    itemsEl.querySelectorAll("[data-remove]").forEach(btn=>btn.addEventListener("click",()=>removeItem(Number(btn.dataset.remove))));
  } catch(e) { itemsEl.innerHTML=`<div class="loading-state">Could not load your bag. ${escapeHtml(e.message)}</div>`; }
}
function changeQuantity(id,delta) {
  const cart=getCart(), item=cart.find(x=>Number(x.id)===id), p=productMap.get(id); if(!item||!p)return;
  const next=item.quantity+delta;
  if(next<1){removeItem(id);return;}
  if(next>p.stock){toast("You have reached the available stock limit.","error");return;}
  item.quantity=next;saveCart(cart);renderCart();
}
function removeItem(id){saveCart(getCart().filter(x=>Number(x.id)!==id));toast("Item removed from your bag.");renderCart();}
