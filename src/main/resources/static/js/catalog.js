const $ = id => document.getElementById(id);

const csrfToken =
    document.querySelector('meta[name="_csrf"]')?.content || '';

const csrfParameter =
    document.querySelector('meta[name="_csrf_parameter"]')?.content || '_csrf';

const loggedIn = document.body.dataset.loggedIn === 'true';
const customerAccount = document.body.dataset.customer === 'true';


const ui = {
    keyword: $('keyword'),
    category: $('category'),
    brand: $('brand'),
    minPrice: $('minPrice'),
    maxPrice: $('maxPrice'),
    stock: $('inStockOnly'),
    search: $('searchButton'),
    filters: $('advancedFilters'),
    filterToggle: $('filterToggle'),
    filterCount: $('filterCount'),
    apply: $('applyFilters'),
    reset: $('resetFilters'),
    clear: $('clearAll'),
    grid: $('productGrid'),
    count: $('resultCount'),
    label: $('resultLabel'),
    empty: $('emptyState'),
    loading: $('loadingState'),
    error: $('searchError'),
    previous: $('previousPage'),
    next: $('nextPage'),
    pageIndicator: $('pageIndicator')
};

const state = {
    page: 0,
    size: 12,
    totalPages: 0
};

function readUrl() {
    const p = new URLSearchParams(location.search);

    ui.keyword.value = p.get('keyword') || '';
    ui.category.value = p.get('category') || '';
    ui.brand.value = p.get('brand') || '';
    ui.minPrice.value = p.get('minPrice') || '';
    ui.maxPrice.value = p.get('maxPrice') || '';
    ui.stock.checked = p.get('inStockOnly') === 'true';

    state.page = Number(p.get('page') || 0);

    if (filterCount() > 0) {
        toggleFilters(true);
    }
}

function filterCount() {
    return [
        ui.category.value,
        ui.brand.value,
        ui.minPrice.value,
        ui.maxPrice.value,
        ui.stock.checked ? '1' : ''
    ].filter(Boolean).length;
}

function updateFilterCount() {
    const count = filterCount();

    ui.filterCount.textContent = count;
    ui.filterCount.hidden = count === 0;
}

function toggleFilters(open) {
    ui.filters.hidden = !open;
    ui.filterToggle.setAttribute('aria-expanded', String(open));
}

function params() {
    const p = new URLSearchParams();

    if (ui.keyword.value.trim()) p.set('keyword', ui.keyword.value.trim());
    if (ui.category.value) p.set('category', ui.category.value);
    if (ui.brand.value) p.set('brand', ui.brand.value);
    if (ui.minPrice.value) p.set('minPrice', ui.minPrice.value);
    if (ui.maxPrice.value) p.set('maxPrice', ui.maxPrice.value);
    if (ui.stock.checked) p.set('inStockOnly', 'true');

    p.set('page', state.page);
    p.set('size', state.size);

    return p;
}

function syncUrl() {
    const p = params();

    p.delete('size');

    if (state.page === 0) {
        p.delete('page');
    }

    history.replaceState(
        null,
        '',
        p.toString() ? `${location.pathname}?${p}` : location.pathname
    );
}

function money(value) {
    return new Intl.NumberFormat('en-LK', {
        style: 'currency',
        currency: 'LKR',
        maximumFractionDigits: 0
    }).format(Number(value || 0));
}

function fallback(product) {
    const visual = document.createElement('div');
    visual.className = 'en-product-fallback';

    const label = document.createElement('span');
    label.textContent = 'ELECTRONEST';

    const initial = document.createElement('strong');
    initial.textContent = (product.name || 'E').charAt(0).toUpperCase();

    visual.append(label, initial);
    return visual;
}

function card(product) {
    const article = document.createElement('article');
    article.className = 'en-market-card';

    const media = document.createElement('div');
    media.className = 'en-market-media';

    if (product.imageUrl) {
        const img = document.createElement('img');
        img.src = product.imageUrl;
        img.alt = product.name || 'Product';
        img.loading = 'lazy';

        img.addEventListener('error', () => {
            img.replaceWith(fallback(product));
        });

        media.append(img);
    } else {
        media.append(fallback(product));
    }

    const unavailable =
        product.outOfStock || Number(product.stockQuantity || 0) <= 0;

    const stock = document.createElement('span');
    stock.className = unavailable
        ? 'en-card-stock en-card-stock-out'
        : 'en-card-stock';

    stock.textContent = unavailable
        ? 'Out of stock'
        : `${product.stockQuantity ?? 0} available`;

    media.append(stock);

    const body = document.createElement('div');
    body.className = 'en-market-card-body';

    const meta = document.createElement('div');
    meta.className = 'en-product-meta';

    const brand = document.createElement('span');
    brand.textContent = product.brand || 'Other';

    const category = document.createElement('span');
    category.textContent = product.category || 'Electronics';

    meta.append(brand, category);

    const title = document.createElement('h2');
    title.textContent = product.name || 'Unnamed product';

    const titleLink = document.createElement('a');
    titleLink.href = `/products/${product.id}`;
    titleLink.append(title);

    const description = document.createElement('p');
    description.textContent =
        product.description || 'Electronics marketplace listing.';

    const footer = document.createElement('div');
    footer.className = 'en-market-card-footer';

    const price = document.createElement('strong');
    price.textContent = money(product.price);

    const cartForm = document.createElement('form');
    cartForm.method = 'post';
    cartForm.action = '/cart/add';
    cartForm.className = 'en-add-cart-form';

    const productInput = document.createElement('input');
    productInput.type = 'hidden';
    productInput.name = 'productId';
    productInput.value = product.id;

    const quantityInput = document.createElement('input');
    quantityInput.type = 'hidden';
    quantityInput.name = 'quantity';
    quantityInput.value = '1';

    const addButton = document.createElement('button');
    addButton.type = 'submit';
    addButton.className = 'en-add-cart-btn';

    const availableStock = Number(product.stockQuantity ?? 0);
    const canAdd = availableStock > 0 && product.outOfStock !== true;

    addButton.textContent = canAdd ? 'Add to cart' : 'Out of stock';
    addButton.disabled = !canAdd;

    if (customerAccount) {
        if (csrfToken) {
            const csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = csrfParameter;
            csrfInput.value = csrfToken;
            cartForm.append(csrfInput);
        }

        cartForm.append(productInput, quantityInput, addButton);
    } else {
        addButton.type = 'button';

        if (!canAdd) {
            addButton.textContent = 'Out of stock';
            addButton.disabled = true;
        } else if (loggedIn) {
            addButton.textContent = 'Customer account required';
            addButton.disabled = true;
        } else {
            addButton.textContent = 'Sign in to add';
            addButton.addEventListener('click', () => {
                location.href = '/login';
            });
        }

        cartForm.append(addButton);
    }

    const arrow = document.createElement('a');
    arrow.className = 'en-card-arrow';
    arrow.href = `/products/${product.id}`;
    arrow.textContent = 'View details ↗';

    footer.append(price, cartForm, arrow);
    body.append(meta, titleLink, description, footer);
    article.append(media, body);

    return article;
}

async function load() {
    if (!validateFilters()) return;

    ui.loading.hidden = false;
    ui.error.hidden = true;
    ui.empty.hidden = true;
    ui.grid.replaceChildren();

    updateFilterCount();
    syncUrl();

    try {
        const response = await fetch(`/api/search/products?${params()}`);

        if (!response.ok) throw new Error();

        const data = await response.json();
        const products = data.content || [];

        state.totalPages = data.totalPages || 0;

        const total = data.totalElements ?? products.length;

        ui.count.textContent = total;
        ui.label.textContent = total === 1 ? 'product' : 'products';

        products.forEach(product => ui.grid.append(card(product)));

        ui.empty.hidden = products.length !== 0;

        ui.previous.disabled = state.page <= 0;
        ui.next.disabled =
            state.totalPages === 0 ||
            state.page >= state.totalPages - 1;

        ui.pageIndicator.textContent =
            state.totalPages
                ? `Page ${state.page + 1} of ${state.totalPages}`
                : 'Page 1';

        ui.clear.hidden =
            !ui.keyword.value.trim() &&
            filterCount() === 0;

    } catch {
        ui.empty.hidden = false;
        ui.empty.querySelector('h2').textContent = 'Shop unavailable.';
        ui.empty.querySelector('p').textContent = 'Please try again.';
        ui.count.textContent = '—';
    } finally {
        ui.loading.hidden = true;
    }
}

function validateFilters() {
    const minimum = ui.minPrice.value === '' ? null : Number(ui.minPrice.value);
    const maximum = ui.maxPrice.value === '' ? null : Number(ui.maxPrice.value);
    let message = '';

    if ((minimum !== null && minimum < 0) || (maximum !== null && maximum < 0)) {
        message = 'Prices cannot be negative.';
    } else if (minimum !== null && maximum !== null && minimum > maximum) {
        message = 'Minimum price cannot exceed maximum price.';
    }

    ui.error.textContent = message;
    ui.error.hidden = !message;
    return !message;
}

function search() {
    state.page = 0;
    load();
}

ui.search.addEventListener('click', search);

ui.keyword.addEventListener('keydown', event => {
    if (event.key === 'Enter') search();
});

ui.filterToggle.addEventListener('click', () => {
    toggleFilters(ui.filters.hidden);
});

ui.apply.addEventListener('click', search);

ui.reset.addEventListener('click', () => {
    ui.category.value = '';
    ui.brand.value = '';
    ui.minPrice.value = '';
    ui.maxPrice.value = '';
    ui.stock.checked = false;
    search();
});

ui.clear.addEventListener('click', () => {
    ui.keyword.value = '';
    ui.category.value = '';
    ui.brand.value = '';
    ui.minPrice.value = '';
    ui.maxPrice.value = '';
    ui.stock.checked = false;

    state.page = 0;
    toggleFilters(false);
    load();
});

ui.previous.addEventListener('click', () => {
    if (state.page > 0) {
        state.page--;
        load();
    }
});

ui.next.addEventListener('click', () => {
    if (state.page < state.totalPages - 1) {
        state.page++;
        load();
    }
});

readUrl();
updateFilterCount();
load();
