document.addEventListener('DOMContentLoaded', () => {
    const list = document.querySelector('.en-order-list');

    if (!list) {
        return;
    }

    const cards = Array.from(
        list.querySelectorAll('.en-order-card')
    );

    if (!cards.length) {
        return;
    }

    function category(card) {
        const statusElement = card.querySelector('.en-status');
        const status = statusElement
            ? statusElement.textContent.trim().toUpperCase()
            : '';

        if (status === 'CANCELLED') {
            return 'cancelled';
        }

        if (status === 'DELIVERED') {
            return 'completed';
        }

        return 'running';
    }

    const counts = {
        running: 0,
        completed: 0,
        cancelled: 0
    };

    cards.forEach(card => {
        card.dataset.orderCategory = category(card);
        counts[card.dataset.orderCategory]++;
    });

    const tabs = document.createElement('div');
    tabs.className = 'en-order-tabs';
    tabs.setAttribute('aria-label', 'Order status filters');

    const definitions = [
        ['running', 'Running'],
        ['completed', 'Completed'],
        ['cancelled', 'Cancelled']
    ];

    const empty = document.createElement('div');
    empty.className = 'en-card en-empty en-order-tab-empty';

    function show(filter) {
        let visible = 0;

        cards.forEach(card => {
            const matches =
                card.dataset.orderCategory === filter;

            card.style.display = matches ? '' : 'none';

            if (matches) {
                visible++;
            }
        });

        const url = new URL(location.href);
        url.searchParams.set('status', filter);
        history.replaceState(null, '', url);
        tabs.querySelectorAll('button').forEach(button => {
            button.setAttribute('aria-pressed', String(button.dataset.filter === filter));
            button.classList.toggle(
                'active',
                button.dataset.filter === filter
            );
        });

        empty.textContent =
            'No ' + filter + ' orders.';
        empty.style.display = visible ? 'none' : 'block';
    }

    definitions.forEach(([filter, label]) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.filter = filter;
        button.textContent =
            label + ' (' + counts[filter] + ')';

        button.addEventListener(
            'click',
            () => show(filter)
        );

        tabs.appendChild(button);
    });

    list.parentNode.insertBefore(tabs, list);
    list.parentNode.insertBefore(empty, list);

    const requested = new URLSearchParams(location.search).get('status');
    const initial =
        location.hash === '#reviews' && counts.completed > 0
            ? 'completed'
            : definitions.some(([key]) => key === requested) ? requested
            : counts.running > 0
            ? 'running'
            : counts.completed > 0
                ? 'completed'
                : 'cancelled';

    show(initial);

});
