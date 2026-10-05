(() => {
    const input = document.getElementById('deliveryPostalCode');
    const error = document.getElementById('deliveryPostalCodeError');
    if (!input || !error) return;

    const message = 'Postal code must be 3 to 10 characters and contain only letters, numbers, spaces, or hyphens.';
    function validate() {
        const valid = input.value === '' || /^[A-Za-z0-9 -]{3,10}$/.test(input.value.trim());
        input.setCustomValidity(valid ? '' : message);
        input.setAttribute('aria-invalid', String(!valid));
        error.textContent = valid ? '' : message;
        error.hidden = valid;
        return valid;
    }

    input.addEventListener('input', validate);
    input.addEventListener('blur', () => {
        if (validate()) input.value = input.value.trim();
    });
    input.addEventListener('invalid', validate);
    input.form.addEventListener('submit', event => {
        if (!validate()) {
            event.preventDefault();
            input.reportValidity();
        } else {
            input.value = input.value.trim();
        }
    });
})();
