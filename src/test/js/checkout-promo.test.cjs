const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function checkout(editing = false) {
    const elements = new Map();
    const element = id => {
        if (!elements.has(id)) elements.set(id, { textContent: '', value: '', disabled: false });
        return elements.get(id);
    };
    const buttons = [{ disabled: false }, { disabled: false }];
    const pending = [];
    let submit;
    const form = element('promoForm');
    form.action = { namedButtons: true }; // HTML named controls shadow the action property.
    form.getAttribute = name => name === 'action' ? '/checkout/promo' : null;
    form.querySelectorAll = () => buttons;
    form.addEventListener = (_, listener) => { submit = listener; };
    element('deliveryForm').style = { display: editing ? 'block' : 'none' };
    element('deliveryAddressLine1').value = 'Unsaved address';
    element('paymentContinueButton').disabled = editing;
    const context = vm.createContext({
        document: { getElementById: element }, URLSearchParams,
        FormData: class { *[Symbol.iterator]() { yield ['_csrf', 'token']; yield ['code', 'WELCOME10']; } },
        fetch: (url, options) => new Promise((resolve, reject) => pending.push({ url, options, resolve, reject }))
    });
    vm.runInContext(fs.readFileSync('src/main/resources/static/js/checkout-promo.js', 'utf8'), context);
    return { element, buttons, pending, submit: action => submit({ preventDefault() {}, submitter: { value: action } }) };
}

const applied = { quote: { code: 'WELCOME10', subtotal: 1000, discount: 100, total: 900 }, error: null };

test('Apply uses form URL despite named action controls and preserves delivery edits', async () => {
    const app = checkout(true);
    const done = app.submit('apply');
    assert.equal(app.pending[0].url, '/checkout/promo');
    assert.equal(app.pending[0].options.body.get('_csrf'), 'token');
    assert.equal(app.pending[0].options.body.get('action'), 'apply');
    app.pending[0].resolve({ json: async () => applied });
    await done;
    assert.equal(app.element('deliveryAddressLine1').value, 'Unsaved address');
    assert.equal(app.element('paymentContinueButton').disabled, true);
    assert.equal(app.element('promoFeedback').textContent, 'WELCOME10 applied.');
    assert.match(app.element('checkoutTotal').textContent, /900\.00/);
});

test('In-flight promo disables payment and suppresses duplicate submissions', async () => {
    const app = checkout();
    const done = app.submit('apply');
    assert.equal(app.element('paymentContinueButton').disabled, true);
    await app.submit('apply');
    assert.equal(app.pending.length, 1);
    app.pending[0].resolve({ json: async () => applied });
    await done;
    assert.equal(app.element('paymentContinueButton').disabled, false);
});

test('Invalid code resets displayed discount and total without losing delivery text', async () => {
    const app = checkout(true);
    const done = app.submit('apply');
    app.pending[0].resolve({ json: async () => ({ quote: { code: null, subtotal: 1000, discount: 0, total: 1000 }, error: 'Invalid code. Continue at full price.' }) });
    await done;
    assert.equal(app.element('promoCode').value, '');
    assert.match(app.element('checkoutTotal').textContent, /1,000\.00/);
    assert.equal(app.element('deliveryAddressLine1').value, 'Unsaved address');
    assert.equal(app.element('promoFeedback').className, 'en-field-error');
});

test('Network failure allows retry and Remove requests clear the code', async () => {
    const app = checkout();
    const first = app.submit('apply');
    app.pending[0].reject(new Error('Offline'));
    await first;
    assert.ok(app.buttons.every(button => !button.disabled));
    assert.match(app.element('promoFeedback').textContent, /Try again/);
    const retry = app.submit('remove');
    assert.equal(app.pending[1].options.body.get('action'), 'remove');
    app.pending[1].resolve({ json: async () => ({ quote: { code: null, subtotal: 1000, discount: 0, total: 1000 }, error: null }) });
    await retry;
    assert.equal(app.element('promoFeedback').textContent, 'Promo code removed.');
});
