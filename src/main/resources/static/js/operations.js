(() => {
    'use strict';
    const loading = document.querySelector('[data-report-loading]');
    const form = document.querySelector('[data-report-filter]');
    if (!loading || !form) return;
    form.addEventListener('submit', () => { loading.hidden = false; form.setAttribute('aria-busy', 'true'); });
    window.addEventListener('pageshow', () => { loading.hidden = true; form.removeAttribute('aria-busy'); });
})();
