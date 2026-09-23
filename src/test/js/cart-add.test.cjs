const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function setup() {
    let submit;
    const pending = [];
    const button = { disabled: false };
    const feedback = { textContent: '', setAttribute() {} };
    const form = {
        dataset: { cartAddUrl: '/api/cart/items' },
        matches: () => true,
        querySelector: selector => selector.startsWith('button') ? button : feedback
    };
    const context = vm.createContext({
        document: { addEventListener: (_, handler) => { submit = handler; } },
        URLSearchParams, TypeError, SyntaxError,
        FormData: class { *[Symbol.iterator]() { yield ['productId', '7']; yield ['quantity', '2']; yield ['_csrf', 'token']; } },
        fetch: (url, options) => new Promise((resolve, reject) => pending.push({ url, options, resolve, reject }))
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/js/cart-add.js', 'utf8'), context);
    let prevented = false;
    return { pending, button, feedback, form, prevented: () => prevented,
        submit: () => submit({ target: form, preventDefault() { prevented = true; } }) };
}

test('Adds in place with quantity and CSRF, blocks duplicates, and permits another purchase', async () => {
    const app = setup();
    const done = app.submit();
    assert.equal(app.prevented(), true);
    assert.equal(app.button.disabled, true);
    await app.submit();
    assert.equal(app.pending.length, 1);
    assert.equal(app.pending[0].url, '/api/cart/items');
    assert.equal(app.pending[0].options.body.get('quantity'), '2');
    assert.equal(app.pending[0].options.body.get('_csrf'), 'token');
    app.pending[0].resolve({ ok: true, json: async () => ({ totalItems: 2 }) });
    await done;
    assert.equal(app.feedback.textContent, 'Added to cart.');
    assert.equal(app.button.disabled, false);
    const next = app.submit();
    assert.equal(app.pending.length, 2);
    app.pending[1].resolve({ ok: true, json: async () => ({ totalItems: 4 }) });
    await next;
});

test('Stock errors remain visible without leaving the page', async () => {
    const app = setup();
    const done = app.submit();
    app.pending[0].resolve({ ok: false, status: 409, json: async () => ({ message: 'Not enough stock' }) });
    await done;
    assert.equal(app.feedback.textContent, 'Not enough stock');
    assert.equal(app.button.disabled, false);
});

test('Expired sessions and denied requests never report success', async () => {
    for (const response of [{ status: 401 }, { status: 403 }, { redirected: true }]) {
        const app = setup();
        const done = app.submit();
        app.pending[0].resolve(response);
        await done;
        assert.match(app.feedback.textContent, /active customer account/);
        assert.equal(app.button.disabled, false);
    }
});

test('Network failures advise checking the cart before retrying', async () => {
    const app = setup();
    const done = app.submit();
    app.pending[0].reject(new TypeError('Offline'));
    await done;
    assert.match(app.feedback.textContent, /Check your cart/);
    assert.equal(app.button.disabled, false);
});

test('Unrelated forms keep their normal submission', async () => {
    const app = setup();
    app.form.matches = () => false;
    await app.submit();
    assert.equal(app.prevented(), false);
    assert.equal(app.pending.length, 0);
});
