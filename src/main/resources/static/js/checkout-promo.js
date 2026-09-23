(() => {
    const form = document.getElementById('promoForm');
    if (!form) return;
    const feedback = document.getElementById('promoFeedback');
    const money = value => Number(value).toLocaleString('en-LK', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    let updating = false;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (updating) return;
        updating = true;
        const continueButton = document.getElementById('paymentContinueButton');
        const continueWasDisabled = continueButton?.disabled;
        if (continueButton) continueButton.disabled = true;
        const data = new URLSearchParams(new FormData(form));
        data.set('action', event.submitter?.value || 'apply');
        const buttons = form.querySelectorAll('button');
        buttons.forEach(button => button.disabled = true);
        try {
            const response = await fetch(form.getAttribute('action'), {
                method: 'POST', body: data, headers: {'Accept': 'application/json'}
            });
            const result = await response.json();
            if (!result.quote) throw new Error('Unexpected response');
            document.getElementById('checkoutSubtotal').textContent = 'Rs. ' + money(result.quote.subtotal);
            document.getElementById('checkoutDiscount').textContent = '− Rs. ' + money(result.quote.discount);
            document.getElementById('checkoutTotal').textContent = 'Rs. ' + money(result.quote.total);
            document.getElementById('promoCode').value = result.quote.code || '';
            feedback.className = result.error ? 'en-field-error' : 'en-muted';
            feedback.textContent = result.error || (result.quote.code ? result.quote.code + ' applied.' : 'Promo code removed.');
        } catch (error) {
            feedback.className = 'en-field-error';
            feedback.textContent = 'Could not update the promo code. Try again or refresh checkout to confirm the total.';
        } finally {
            updating = false;
            if (continueButton) continueButton.disabled = continueWasDisabled
                || document.getElementById('deliveryForm')?.style.display !== 'none';
            buttons.forEach(button => button.disabled = false);
        }
    });
})();
