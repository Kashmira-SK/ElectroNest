const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function field(value) {
    const events = {};
    const error = { hidden: true, textContent: '' };
    const input = {
        value, attributes: {}, validityMessage: '',
        addEventListener(name, handler) { events[name] = handler; },
        setCustomValidity(message) { this.validityMessage = message; },
        setAttribute(name, value) { this.attributes[name] = value; },
        reportValidity() { this.reported = true; },
        form: { addEventListener(name, handler) { events[name] = handler; } }
    };
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/checkout-postal-code.js', 'utf8'), {
        document: { getElementById: id => id === 'deliveryPostalCode' ? input : error }
    });
    return { input, error, events };
}

test('optional empty and supported postal formats submit trimmed', () => {
    for (const value of ['', '101', '10100', 'SW1A 1AA', '12345-6789', '  AB-123  ']) {
        const { input, error, events } = field(value);
        events.submit({ preventDefault() { assert.fail('valid input blocked'); } });
        assert.equal(input.value, value.trim());
        assert.equal(input.validityMessage, '');
        assert.equal(error.hidden, true);
    }
});

test('invalid postal codes show an inline error and preserve the submitted text', () => {
    for (const value of [' ', '   ', '12', '12345678901', '12@45', '12/45', 'AB_123']) {
        const { input, error, events } = field(value);
        let prevented = false;
        events.submit({ preventDefault() { prevented = true; } });
        assert.equal(prevented, true);
        assert.equal(input.value, value);
        assert.equal(error.hidden, false);
        assert.match(error.textContent, /Postal code must be 3 to 10 characters/);
        assert.equal(input.attributes['aria-invalid'], 'true');
        assert.equal(input.reported, true);
    }
});

test('correcting an invalid value clears the error, and blur trims valid input', () => {
    const { input, error, events } = field('12@45');
    events.input();
    input.value = '  SW1A 1AA  ';
    events.input();
    assert.equal(input.validityMessage, '');
    assert.equal(error.hidden, true);
    assert.equal(input.attributes['aria-invalid'], 'false');
    events.blur();
    assert.equal(input.value, 'SW1A 1AA');
});
