document.querySelectorAll('[data-product-quantity]').forEach(control => {
    const input = control.querySelector('input[name="quantity"]');
    const decrease = control.querySelector('[data-quantity-step="-1"]');
    const increase = control.querySelector('[data-quantity-step="1"]');

    function updateButtons() {
        decrease.disabled = input.disabled || input.valueAsNumber <= Number(input.min);
        increase.disabled = input.disabled || input.valueAsNumber >= Number(input.max);
    }

    decrease.addEventListener('click', () => {
        input.stepDown();
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    increase.addEventListener('click', () => {
        input.stepUp();
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    input.addEventListener('input', updateButtons);
    input.addEventListener('change', updateButtons);
    updateButtons();
});
