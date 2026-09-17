const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function shop(search = '', dataset = {}) {
    const elements = new Map();
    const pending = [];
    const element = id => {
        if (!elements.has(id)) elements.set(id, {
            value: '', checked: false, hidden: true, textContent: '',
            validity: { badInput: false },
            addEventListener() {}, setAttribute() {}, replaceChildren() {}, append() {}
        });
        return elements.get(id);
    };
    const context = vm.createContext({
        document: { getElementById: element, querySelector: () => null, body: { dataset } },
        location: { search, pathname: '/products' },
        history: { replaceState() {} }, URLSearchParams, AbortController, Intl,
        fetch: () => new Promise((resolve, reject) => pending.push({ resolve, reject }))
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/js/catalog.js', 'utf8'), context);
    const finish = async (index, total) => {
        pending[index].resolve({ ok: true, json: async () => ({ content: [], totalPages: 1, totalElements: total }) });
        await new Promise(resolve => setImmediate(resolve));
    };
    return { context, element, pending, finish };
}

test('latest search wins even when an older response arrives later', async () => {
    const app = shop();
    const latest = vm.runInContext('load()', app.context);
    await app.finish(1, 2);
    await latest;
    await app.finish(0, 99);
    assert.equal(app.element('resultCount').textContent, 2);
});

test('failed search can recover to a normal empty state', async () => {
    const app = shop();
    app.pending[0].reject(new Error('offline'));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(app.element('searchError').hidden, false);
    assert.equal(app.element('nextPage').disabled, true);
    const retry = vm.runInContext('load()', app.context);
    await app.finish(1, 0);
    await retry;
    assert.equal(app.element('searchError').hidden, true);
    assert.equal(app.element('emptyState').hidden, false);
});

test('invalid URL page resets and price fractions are preserved', () => {
    const app = shop('?page=-2');
    assert.equal(vm.runInContext('state.page', app.context), 0);
    assert.match(vm.runInContext('money(10.25)', app.context), /10\.25/);
});

test('active customer receives a working cart action', () => {
    const app = shop('', { loggedIn: 'true', customer: 'true', suspended: 'false' });
    const state = vm.runInContext('purchaseState(true)', app.context);
    assert.equal(state.mode, 'cart');
    assert.equal(state.label, 'Add to cart');
});

test('vendor receives a disabled customer-only purchase action', () => {
    const app = shop('', { loggedIn: 'true', vendor: 'true', suspended: 'false' });
    const state = vm.runInContext('purchaseState(true)', app.context);
    assert.equal(state.mode, 'disabled');
    assert.equal(state.label, 'Customer accounts only');
});

test('suspended customer receives a disabled purchase action', () => {
    const app = shop('', { loggedIn: 'true', customer: 'true', suspended: 'true' });
    const state = vm.runInContext('purchaseState(true)', app.context);
    assert.equal(state.mode, 'disabled');
    assert.equal(state.label, 'Account suspended');
});

test('guest keeps login-to-purchase behavior', () => {
    const app = shop();
    const state = vm.runInContext('purchaseState(true)', app.context);
    assert.equal(state.mode, 'login');
    assert.equal(state.label, 'Log in to purchase');
});
