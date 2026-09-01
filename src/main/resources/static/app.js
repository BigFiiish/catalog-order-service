const state = {
    connected: false,
    page: 0,
    size: 4,
    totalPages: 0,
    products: [],
    cart: new Map(),
    lastOrder: null,
    lastSuccessfulRequest: null,
    raceBody: null
};

const elements = {
    apiKey: document.querySelector("#apiKey"),
    connectButton: document.querySelector("#connectButton"),
    connectionStatus: document.querySelector("#connectionStatus"),
    categoryFilter: document.querySelector("#categoryFilter"),
    productGrid: document.querySelector("#productGrid"),
    previousPage: document.querySelector("#previousPage"),
    nextPage: document.querySelector("#nextPage"),
    pageLabel: document.querySelector("#pageLabel"),
    customerEmail: document.querySelector("#customerEmail"),
    idempotencyKey: document.querySelector("#idempotencyKey"),
    regenerateKey: document.querySelector("#regenerateKey"),
    cartItems: document.querySelector("#cartItems"),
    cartCount: document.querySelector("#cartCount"),
    orderTotal: document.querySelector("#orderTotal"),
    createOrderButton: document.querySelector("#createOrderButton"),
    replayButton: document.querySelector("#replayButton"),
    lastUnitButton: document.querySelector("#lastUnitButton"),
    shipFocusButton: document.querySelector("#shipFocusButton"),
    clearTrace: document.querySelector("#clearTrace"),
    traceLog: document.querySelector("#traceLog"),
    toast: document.querySelector("#toast"),
    orderDialog: document.querySelector("#orderDialog"),
    closeDialog: document.querySelector("#closeDialog"),
    resultEyebrow: document.querySelector("#resultEyebrow"),
    resultTitle: document.querySelector("#resultTitle"),
    resultStatus: document.querySelector("#resultStatus"),
    resultDetails: document.querySelector("#resultDetails"),
    dialogReplayButton: document.querySelector("#dialogReplayButton"),
    newOrderButton: document.querySelector("#newOrderButton"),
    shippingBox: document.querySelector("#shippingBox"),
    webhookUrl: document.querySelector("#webhookUrl"),
    shipOrderButton: document.querySelector("#shipOrderButton")
};

class ApiError extends Error {
    constructor(status, payload) {
        super(payload?.message || `Request failed with HTTP ${status}`);
        this.status = status;
        this.payload = payload;
    }
}

function newIdempotencyKey() {
    const value = typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : `order-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    elements.idempotencyKey.value = value;
    return value;
}

function money(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD"
    }).format(Number(value));
}

function initials(name) {
    return name.split(/\s+/).map(word => word[0]).join("").slice(0, 2).toUpperCase();
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function showToast(message, kind = "success") {
    elements.toast.textContent = message;
    elements.toast.style.borderLeftColor = kind === "error" ? "#ff8e7d" : "#bdf05a";
    elements.toast.classList.add("visible");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => elements.toast.classList.remove("visible"), 3500);
}

function setConnection(status, label) {
    elements.connectionStatus.className = `status-pill ${status === "connected" ? "status-connected" : "status-waiting"}`;
    elements.connectionStatus.innerHTML = `<span class="status-dot"></span> ${escapeHtml(label)}`;
}

function explanationFor(method, path, status) {
    if (status === 401) return "Rejected at the API boundary before business logic runs.";
    if (method === "GET") return "Read-only JDBC query with deterministic ordering and pagination metadata.";
    if (path.endsWith("/ship") && status === 200) return "CREATED → SHIPPED committed first; webhook delivery continues asynchronously.";
    if (path.endsWith("/ship") && status === 409) return "The conditional state transition affected zero rows, preventing a second shipment.";
    if (method === "POST" && path === "/api/orders" && status === 201) return "Order, items, and stock deductions committed together in one transaction.";
    if (method === "POST" && path === "/api/orders" && status === 200) return "The unique idempotency key matched an existing success, so the original order was replayed.";
    if (method === "POST" && path === "/api/orders" && status === 409) return "A conditional stock update affected zero rows; the transaction rolled back every prior write.";
    if (status >= 400) return "The API returned a specific, structured error without leaving partial state.";
    return "Request completed successfully.";
}

function addTrace(method, path, status, elapsed, payload) {
    const empty = elements.traceLog.querySelector(".trace-empty");
    if (empty) empty.remove();

    const entry = document.createElement("article");
    entry.className = "trace-entry";
    entry.innerHTML = `
        <div class="trace-entry-head">
            <span class="trace-method">${escapeHtml(method)}</span>
            <span class="trace-path">${escapeHtml(path)}</span>
            <span class="trace-status ${status >= 400 ? "is-error" : ""}">HTTP ${status}</span>
        </div>
        <p class="trace-explanation">${escapeHtml(explanationFor(method, path, status))}</p>
        <div class="trace-meta">${new Date().toLocaleTimeString()} · ${elapsed} ms</div>
        <details>
            <summary>Inspect response</summary>
            <pre>${escapeHtml(JSON.stringify(payload, null, 2))}</pre>
        </details>`;
    elements.traceLog.prepend(entry);
}

async function apiRequest(path, options = {}) {
    const method = options.method || "GET";
    const start = performance.now();
    const headers = new Headers(options.headers || {});
    headers.set("X-API-Key", elements.apiKey.value.trim());

    const response = await fetch(path, {...options, headers});
    const text = await response.text();
    let payload = null;

    if (text) {
        try { payload = JSON.parse(text); }
        catch { payload = {message: text}; }
    }

    addTrace(method, path, response.status, Math.round(performance.now() - start), payload);

    if (!response.ok) throw new ApiError(response.status, payload);
    return {status: response.status, data: payload};
}

async function connect() {
    elements.connectButton.disabled = true;
    elements.connectButton.textContent = "Checking…";
    setConnection("waiting", "Connecting");

    try {
        state.page = 0;
        await loadProducts();
        state.connected = true;
        setConnection("connected", "Connected");
        elements.connectButton.textContent = "Refresh";
        if (elements.idempotencyKey.value.startsWith("Generated")) newIdempotencyKey();
        renderCart();
        showToast("Connected to the Spring Boot API.");
    } catch (error) {
        state.connected = false;
        setConnection("waiting", error.status === 401 ? "Unauthorized" : "Unavailable");
        elements.connectButton.textContent = "Try again";
        renderCatalogError(error.message);
        showToast(error.message, "error");
    } finally {
        elements.connectButton.disabled = false;
    }
}

async function loadProducts() {
    elements.productGrid.innerHTML = `<div class="catalog-message"><strong>Loading catalog…</strong><span>Querying the product repository.</span></div>`;
    const category = elements.categoryFilter.value;
    const query = new URLSearchParams({page: state.page, size: state.size});
    if (category) query.set("category", category);

    const result = await apiRequest(`/api/products?${query}`);
    state.products = result.data.items;
    state.totalPages = result.data.totalPages;
    renderProducts();
    renderPagination();
}

function renderCatalogError(message) {
    elements.productGrid.innerHTML = `<div class="catalog-message"><strong>Catalog unavailable</strong><span>${escapeHtml(message)}</span></div>`;
}

function stockLabel(product) {
    if (product.stockQuantity === 0) return {className: "stock-out", text: "Out of stock"};
    if (product.stockQuantity === 1) return {className: "stock-low", text: "Last one"};
    return {className: "stock-good", text: `${product.stockQuantity} in stock`};
}

function renderProducts() {
    if (!state.products.length) {
        elements.productGrid.innerHTML = `<div class="catalog-message"><strong>No matching products</strong><span>Try another category.</span></div>`;
        return;
    }

    elements.productGrid.innerHTML = state.products.map(product => {
        const stock = stockLabel(product);
        const visualClass = `visual-${product.category.toLowerCase()}`;
        return `
            <article class="product-card">
                <div class="product-visual ${visualClass}">
                    <span>${String(product.id).padStart(2, "0")}</span>
                    <b>${escapeHtml(initials(product.name))}</b>
                </div>
                <div class="product-meta">
                    <span>${escapeHtml(product.category)}</span>
                    <span class="${stock.className}">${stock.text}</span>
                </div>
                <h3>${escapeHtml(product.name)}</h3>
                <p class="price">${money(product.price)}</p>
                <button class="button button-outline add-product" data-product-id="${product.id}" ${product.stockQuantity === 0 ? "disabled" : ""}>
                    ${product.stockQuantity === 0 ? "Unavailable" : "Add to order"}
                </button>
            </article>`;
    }).join("");
}

function renderPagination() {
    const displayPage = state.totalPages === 0 ? 0 : state.page + 1;
    elements.pageLabel.textContent = `Page ${displayPage} of ${state.totalPages}`;
    elements.previousPage.disabled = state.page <= 0;
    elements.nextPage.disabled = state.page + 1 >= state.totalPages;
}

async function getProduct(productId) {
    const cached = state.products.find(product => product.id === productId);
    if (cached) return cached;
    const result = await apiRequest(`/api/products/${productId}`);
    return result.data;
}

async function addProduct(productId) {
    try {
        const product = await getProduct(productId);
        if (product.stockQuantity <= 0) throw new Error(`${product.name} is out of stock.`);
        const existing = state.cart.get(product.id);
        const nextQuantity = (existing?.quantity || 0) + 1;
        if (nextQuantity > product.stockQuantity) throw new Error(`Only ${product.stockQuantity} unit(s) are available.`);
        state.cart.set(product.id, {product, quantity: nextQuantity});
        renderCart();
        showToast(`${product.name} added to the order.`);
    } catch (error) {
        showToast(error.message, "error");
    }
}

function changeQuantity(productId, delta) {
    const item = state.cart.get(productId);
    if (!item) return;
    const next = item.quantity + delta;
    if (next <= 0) state.cart.delete(productId);
    else if (next <= item.product.stockQuantity) item.quantity = next;
    else return showToast(`Only ${item.product.stockQuantity} unit(s) are available.`, "error");
    renderCart();
}

function renderCart() {
    const items = [...state.cart.values()];
    const count = items.reduce((sum, item) => sum + item.quantity, 0);
    const total = items.reduce((sum, item) => sum + Number(item.product.price) * item.quantity, 0);

    elements.cartCount.textContent = count;
    elements.orderTotal.textContent = money(total);
    elements.createOrderButton.disabled = !state.connected || items.length === 0;

    if (!items.length) {
        elements.cartItems.innerHTML = `<div class="empty-state"><span>＋</span><p>Your order is empty.</p><small>Add a product from the catalog.</small></div>`;
        return;
    }

    elements.cartItems.innerHTML = items.map(({product, quantity}) => `
        <div class="cart-row">
            <div><h4>${escapeHtml(product.name)}</h4><p>${money(product.price)} each</p></div>
            <div class="quantity-control">
                <button type="button" data-quantity="-1" data-product-id="${product.id}" aria-label="Remove one ${escapeHtml(product.name)}">−</button>
                <span>${quantity}</span>
                <button type="button" data-quantity="1" data-product-id="${product.id}" aria-label="Add one ${escapeHtml(product.name)}">+</button>
            </div>
        </div>`).join("");
}

function currentOrderBody() {
    return {
        customerEmail: elements.customerEmail.value.trim(),
        items: [...state.cart.values()].map(({product, quantity}) => ({productId: product.id, quantity}))
    };
}

async function sendOrder(body, idempotencyKey, source = "create") {
    const result = await apiRequest("/api/orders", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": idempotencyKey
        },
        body: JSON.stringify(body)
    });

    state.lastOrder = result.data;
    if (result.status === 201) state.lastSuccessfulRequest = {body, idempotencyKey};
    elements.replayButton.disabled = false;
    elements.shipFocusButton.disabled = result.data.status !== "CREATED";
    renderOrderResult(result.status, source);
    return result;
}

async function createOrder() {
    const body = currentOrderBody();
    const key = elements.idempotencyKey.value.trim();
    elements.createOrderButton.disabled = true;
    elements.createOrderButton.innerHTML = "Committing…";

    try {
        const result = await sendOrder(body, key);
        if (result.status === 201 && body.items.length === 1 && body.items[0].productId === 4) {
            state.raceBody = body;
            elements.lastUnitButton.dataset.mode = "compete";
            elements.lastUnitButton.textContent = "Simulate second buyer";
        }
        state.cart.clear();
        renderCart();
        await loadProducts();
        showToast(result.status === 201 ? "Order committed successfully." : "Original order safely replayed.");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        elements.createOrderButton.innerHTML = `Create order <span>→</span>`;
        renderCart();
    }
}

function renderOrderResult(httpStatus, source) {
    const order = state.lastOrder;
    const total = order.items.reduce((sum, item) => sum + Number(item.unitPrice) * item.quantity, 0);
    elements.resultEyebrow.textContent = httpStatus === 201 ? "HTTP 201 · New commit" : "HTTP 200 · Safe replay";
    if (source === "ship") elements.resultEyebrow.textContent = "HTTP 200 · State committed";
    elements.resultTitle.textContent = `Order #${order.id}`;
    elements.resultStatus.textContent = order.status;
    elements.resultDetails.innerHTML = `
        <div class="result-line"><span>Customer</span><strong>${escapeHtml(order.customerEmail)}</strong></div>
        <div class="result-line"><span>Items</span><strong>${order.items.reduce((sum, item) => sum + item.quantity, 0)}</strong></div>
        <div class="result-line"><span>Total captured</span><strong>${money(total)}</strong></div>`;
    const shipped = order.status === "SHIPPED";
    elements.shippingBox.classList.toggle("is-shipped", shipped);
    elements.shipOrderButton.disabled = shipped;
    elements.shipOrderButton.innerHTML = shipped ? "Already shipped" : `Mark shipped <span>→</span>`;
    if (!elements.orderDialog.open) elements.orderDialog.showModal();
}

async function replayLastOrder() {
    if (!state.lastSuccessfulRequest) return showToast("Create an order first.", "error");
    try {
        const {body, idempotencyKey} = state.lastSuccessfulRequest;
        await sendOrder(body, idempotencyKey, "replay");
        showToast("Same key returned the original order with HTTP 200.");
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function handleLastUnitDemo() {
    if (!state.connected) {
        showToast("Connect to the API first.", "error");
        return document.querySelector(".connection-card").scrollIntoView({behavior: "smooth"});
    }

    if (elements.lastUnitButton.dataset.mode === "compete" && state.raceBody) {
        try {
            await sendOrder(state.raceBody, newIdempotencyKey(), "race");
            showToast("Unexpectedly created a second order.", "error");
        } catch (error) {
            if (error.status === 409) showToast("Second buyer received 409. No overselling occurred.");
            else showToast(error.message, "error");
        } finally {
            elements.lastUnitButton.dataset.mode = "prepare";
            elements.lastUnitButton.textContent = "Prepare last-unit demo";
            await loadProducts().catch(() => {});
        }
        return;
    }

    try {
        const product = await getProduct(4);
        if (product.stockQuantity < 1) {
            return showToast("Product 4 is already sold out. Restart the app to reset H2.", "error");
        }
        state.cart.clear();
        state.cart.set(4, {product, quantity: 1});
        elements.customerEmail.value = "first-buyer@example.com";
        newIdempotencyKey();
        renderCart();
        document.querySelector(".order-panel").scrollIntoView({behavior: "smooth", block: "center"});
        showToast("Last-unit scenario prepared. Create this order first.");
    } catch (error) {
        showToast(error.message, "error");
    }
}

async function shipOrder() {
    if (!state.lastOrder) return;
    elements.shipOrderButton.disabled = true;
    elements.shipOrderButton.textContent = "Committing state…";
    try {
        const result = await apiRequest(`/api/orders/${state.lastOrder.id}/ship`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({webhookUrl: elements.webhookUrl.value.trim()})
        });
        state.lastOrder = result.data;
        renderOrderResult(result.status, "ship");
        elements.shipFocusButton.disabled = true;
        showToast("Order marked SHIPPED; webhook delivery is running off-path.");
    } catch (error) {
        showToast(error.message, "error");
        elements.shipOrderButton.disabled = false;
        elements.shipOrderButton.innerHTML = `Mark shipped <span>→</span>`;
    }
}

elements.connectButton.addEventListener("click", connect);
elements.categoryFilter.addEventListener("change", async () => {
    state.page = 0;
    if (state.connected) await loadProducts().catch(error => showToast(error.message, "error"));
});
elements.previousPage.addEventListener("click", async () => { state.page--; await loadProducts(); });
elements.nextPage.addEventListener("click", async () => { state.page++; await loadProducts(); });
elements.productGrid.addEventListener("click", event => {
    const button = event.target.closest(".add-product");
    if (button) addProduct(Number(button.dataset.productId));
});
elements.cartItems.addEventListener("click", event => {
    const button = event.target.closest("[data-quantity]");
    if (button) changeQuantity(Number(button.dataset.productId), Number(button.dataset.quantity));
});
elements.regenerateKey.addEventListener("click", () => { newIdempotencyKey(); showToast("Generated a fresh idempotency key."); });
elements.createOrderButton.addEventListener("click", createOrder);
elements.replayButton.addEventListener("click", replayLastOrder);
elements.dialogReplayButton.addEventListener("click", replayLastOrder);
elements.lastUnitButton.addEventListener("click", handleLastUnitDemo);
elements.shipFocusButton.addEventListener("click", () => {
    if (state.lastOrder) renderOrderResult(200, "focus");
});
elements.shipOrderButton.addEventListener("click", shipOrder);
elements.closeDialog.addEventListener("click", () => elements.orderDialog.close());
elements.newOrderButton.addEventListener("click", () => {
    elements.orderDialog.close();
    state.cart.clear();
    newIdempotencyKey();
    renderCart();
    document.querySelector("#catalog").scrollIntoView({behavior: "smooth"});
});
elements.clearTrace.addEventListener("click", () => {
    elements.traceLog.innerHTML = `<div class="trace-empty"><span>READY</span><p>The next API call will start a fresh trace.</p></div>`;
});
elements.orderDialog.addEventListener("click", event => {
    if (event.target === elements.orderDialog) elements.orderDialog.close();
});

newIdempotencyKey();
renderCart();
connect();
