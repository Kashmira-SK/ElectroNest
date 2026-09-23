document.addEventListener('submit', async event => {
    const form = event.target;
    if (!form.matches('form[data-cart-add-url]')) return;
    event.preventDefault();
    if (form.dataset.submitting === 'true') return;

    const button = form.querySelector('button[type="submit"]');
    let feedback = form.querySelector('[role="status"]');
    if (!feedback) {
        feedback = document.createElement('span');
        feedback.setAttribute('role', 'status');
        form.append(feedback);
    }
    form.dataset.submitting = 'true';
    button.disabled = true;
    feedback.className = 'en-muted';
    feedback.textContent = 'Adding…';

    try {
        const response = await fetch(form.dataset.cartAddUrl, {
            method: 'POST',
            headers: { Accept: 'application/json' },
            body: new URLSearchParams(new FormData(form))
        });
        if (response.status === 401 || response.status === 403 || response.redirected) {
            throw new Error('Unable to add this item. Please check that you are signed in with an active customer account.');
        }
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.message || 'Unable to add this item. Please try again.');
        }
        feedback.textContent = 'Added to cart.';
    } catch (error) {
        feedback.className = 'en-field-error';
        feedback.textContent = error instanceof TypeError || error instanceof SyntaxError
            ? 'Could not confirm the update. Check your cart before trying again.'
            : error.message;
    } finally {
        form.dataset.submitting = 'false';
        button.disabled = false;
    }
});
