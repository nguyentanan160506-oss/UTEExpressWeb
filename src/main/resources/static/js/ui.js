(() => {
    'use strict';
    const mobile = window.matchMedia('(max-width: 991.98px)');
    const toggles = document.querySelectorAll('[data-ui-menu]');
    toggles.forEach(button => {
        const panel = document.getElementById(button.getAttribute('aria-controls'));
        if (!panel) return;
        const setOpen = open => {
            panel.hidden = !open;
            button.setAttribute('aria-expanded', String(open));
        };
        const sync = () => {
            button.hidden = !mobile.matches;
            setOpen(!mobile.matches);
        };
        button.addEventListener('click', () => setOpen(panel.hidden));
        panel.addEventListener('keydown', event => {
            if (event.key === 'Escape' && mobile.matches && !panel.hidden) {
                setOpen(false);
                button.focus();
            }
        });
        mobile.addEventListener('change', sync);
        sync();
    });
    // Mark the most specific navigation link; no route, request or permission changes.
    document.querySelectorAll('[data-ui-navigation]').forEach(nav => {
        let best = null;
        nav.querySelectorAll('a[href]').forEach(link => {
            const path = new URL(link.href, location.href).pathname;
            if (path === location.pathname || (path !== '/' && location.pathname.startsWith(path + '/'))) {
                if (!best || path.length > new URL(best.href, location.href).pathname.length) best = link;
            }
        });
        if (best) best.setAttribute('aria-current', 'page');
    });
    // Long operational pages get a keyboard-friendly index of rendered sections.
    // Only existing headings are read; no hidden actions, fields or permission checks change.
    const detail = document.querySelector('[data-ui-sections]');
    if (detail) {
        const headings = [...detail.querySelectorAll('h2')].filter(h => h.textContent.trim());
        if (headings.length >= 3) {
            const index = document.createElement('details');
            index.className = 'section-index';
            const summary = document.createElement('summary');
            summary.textContent = 'Trên trang này';
            const nav = document.createElement('nav');
            nav.setAttribute('aria-label', 'Các phần nội dung trên trang');
            headings.forEach((heading, position) => {
                if (!heading.id) {
                    let id = 'ui-section-' + position;
                    while (document.getElementById(id)) id += '-next';
                    heading.id = id;
                }
                const link = document.createElement('a');
                link.href = '#' + heading.id;
                link.textContent = heading.textContent.trim();
                nav.append(link);
            });
            index.append(summary, nav);
            detail.querySelector('h1')?.insertAdjacentElement('afterend', index);
        }
    }
})();
