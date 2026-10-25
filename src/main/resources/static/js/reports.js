(() => {
    'use strict';
    const form = document.querySelector('[data-report-filter]');
    if (!form) return;
    const dataset = form.querySelector('[data-report-dataset]');
    if (!dataset) return;
    dataset.addEventListener('change', () => {
        const status = form.querySelector('[data-report-status]');
        if (status) status.value = '';
        form.requestSubmit();
    });
})();
