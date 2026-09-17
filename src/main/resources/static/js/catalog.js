const $ = id => document.getElementById(id);

const csrfToken =
    document.querySelector('meta[name="_csrf"]')?.content || '';

const csrfParameter =
    document.querySelector('meta[name="_csrf_parameter"]')?.content || '_csrf';

const loggedIn = document.body.dataset.loggedIn === 'true';
const customerAccount = document.body.dataset.customer === 'true';
const vendorAccount = document.body.dataset.vendor === 'true';
const accountSuspended = document.body.dataset.suspended === 'true';


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

    const page = Number(p.get('page') || 0);
    state.page = Number.isSafeInteger(page) && page >= 0 && page <= 2147483647 ? page : 0;

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
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(Number(value || 0));
}

const productImages = {
    'Keychron K2 Pro': '/images/products/keychron-keyboard.jpg',
    '980 PRO 1TB NVMe SSD': '/images/products/nvme-storage.jpg',
    'G502 X': '/images/products/gaming-mouse.jpg',
    'Portable SSD T7 1TB': '/images/products/portable-ssd.jpg',
    'WH-1000XM5 Wireless Headphones': '/images/products/wireless-headphones.jpg',
    'TUF Gaming VG27AQ3A': '/images/products/gaming-monitor.jpg',
    'MX Master 3S': '/images/products/wireless-mouse.jpg',
    'AirPods Pro': '/images/products/airpods.jpg'
};

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

function purchaseState(available) {
    if (!available) {
        return { mode: 'disabled', label: 'Out of stock' };
    }

    if (!loggedIn) {
        return { mode: 'login', label: 'Log in to purchase' };
    }

    if (accountSuspended) {
        return { mode: 'disabled', label: 'Account suspended' };
    }

    if (customerAccount) {
        return { mode: 'cart', label: 'Add to cart' };
    }

    return {
        mode: 'disabled',
        label: vendorAccount ? 'Customer accounts only' : 'Customer only'
    };
}

function purchaseControl(product, available) {
    const state = purchaseState(available);

    if (state.mode === 'login') {
        const login = document.createElement('a');
        login.href = '/login';
        login.className = 'en-add-cart-btn';
        login.textContent = state.label;
        return login;
    }

    if (state.mode === 'disabled') {
        const disabled = document.createElement('button');
        disabled.type = 'button';
        disabled.className = 'en-add-cart-btn';
        disabled.textContent = state.label;
        disabled.disabled = true;
        return disabled;
    }

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
    addButton.textContent = state.label;

    if (csrfToken) {
        const csrfInput = document.createElement('input');
        csrfInput.type = 'hidden';
        csrfInput.name = csrfParameter;
        csrfInput.value = csrfToken;
        cartForm.append(csrfInput);
    }

    cartForm.append(productInput, quantityInput, addButton);
    return cartForm;
}

function card(product) {
    const detailsUrl = `/products/${product.id}`;
    const article = document.createElement('article');
    article.className = 'en-market-card';
    article.tabIndex = 0;
    article.setAttribute(
        'aria-label',
        `View ${product.name || 'product'} details`
    );

    article.addEventListener('click', event => {
        if (event.target.closest('a, button, form, input')) return;
        location.href = detailsUrl;
    });

    article.addEventListener('keydown', event => {
        if (event.target !== article) return;

        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            location.href = detailsUrl;
        }
    });

    const media = document.createElement('div');
    media.className = 'en-market-media';

    const productLink = document.createElement('a');
    productLink.href = detailsUrl;
    productLink.className = 'en-market-media-link';

    const imageUrl =
        product.imageUrl || product.imageUrls?.[0] || productImages[product.name];

    if (imageUrl) {
        const img = document.createElement('img');
        img.src = imageUrl;
        img.alt = product.name || 'Product';
        img.loading = 'lazy';

        img.addEventListener('error', () => {
            img.replaceWith(fallback(product));
        });

        productLink.append(img);
    } else {
        productLink.append(fallback(product));
    }

    media.append(productLink);

    const unavailable =
        product.outOfStock ||
        Number(product.stockQuantity || 0) <= 0;

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
    category.textContent =
        product.category || 'Electronics';

    meta.append(brand, category);

    const title = document.createElement('h2');

    const titleLink = document.createElement('a');
    titleLink.href = detailsUrl;
    titleLink.textContent =
        product.name || 'Unnamed product';

    title.append(titleLink);

    const description = document.createElement('p');
    description.textContent =
        product.description ||
        'Electronics marketplace listing.';

    const footer = document.createElement('div');
    footer.className = 'en-market-card-footer';

    const price = document.createElement('strong');
    price.textContent = money(product.price);

    const availableStock =
        Number(product.stockQuantity ?? 0);

    const canAdd =
        availableStock > 0 &&
        product.outOfStock !== true;

    footer.append(
        price,
        purchaseControl(product, canAdd)
    );

    body.append(
        meta,
        title,
        description,
        footer
    );

    article.append(media, body);

    return article;
}

let requestVersion = 0;
let activeRequest;

async function load() {
    const version = ++requestVersion;
    activeRequest?.abort();
    if (!validateFilters()) {
        ui.loading.hidden = true;
        ui.previous.disabled = true;
        ui.next.disabled = true;
        ui.pageIndicator.textContent = 'Adjust filters';
        return;
    }
    activeRequest = new AbortController();

    ui.loading.hidden = false;
    ui.previous.disabled = true;
    ui.next.disabled = true;
    ui.pageIndicator.textContent = 'Loading…';
    ui.error.hidden = true;
    ui.empty.hidden = true;
    ui.grid.replaceChildren();

    updateFilterCount();
    syncUrl();

    try {
        const response = await fetch(`/api/search/products?${params()}`, { signal: activeRequest.signal });

        if (!response.ok) throw new Error();

        const data = await response.json();
        if (version !== requestVersion) return;
        const products = data.content || [];

        state.totalPages = data.totalPages || 0;
        if (state.page > 0 && state.page >= state.totalPages) {
            state.page = Math.max(0, state.totalPages - 1);
            return load();
        }

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

    } catch (error) {
        if (version !== requestVersion || error.name === 'AbortError') return;
        ui.error.hidden = false;
        ui.error.textContent = 'Could not load products. Check your connection and press Search to retry.';
        state.totalPages = 0;
        ui.pageIndicator.textContent = 'Unavailable';
        ui.count.textContent = '—';
    } finally {
        if (version === requestVersion) ui.loading.hidden = true;
    }
}

function validateFilters() {
    const minimum = ui.minPrice.value === '' ? null : Number(ui.minPrice.value);
    const maximum = ui.maxPrice.value === '' ? null : Number(ui.maxPrice.value);
    let message = '';

    if (ui.minPrice.validity.badInput || ui.maxPrice.validity.badInput) {
        message = 'Enter valid prices.';
    } else if ((minimum !== null && minimum < 0) || (maximum !== null && maximum < 0)) {
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
