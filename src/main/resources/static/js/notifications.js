/* Private, receive-only STOMP 1.2 client. No client-selected user, destination or message body. */
(() => {
    'use strict';
    if (window.uteNotificationsStarted) return;
    const widget = document.querySelector('[data-notification-widget]');
    if (!widget) return;
    window.uteNotificationsStarted = true;
    const base = widget.dataset.base;
    const center = document.querySelector('[data-notification-center]');
    const badges = document.querySelectorAll('[data-notification-count]');
    const connection = widget.querySelector('[data-notification-connection]');
    const status = document.querySelector('[data-notification-status]');
    const tabs = typeof BroadcastChannel === 'function' ? new BroadcastChannel('ute-notifications') : null;
    let socket, csrf, heartbeat, handshakeTimer, retryTimer, refreshTimer, delay = 1000, stopped = false, loading = false, dirty = false, buffer = '', lastReceived = 0;
    function note(text) { if (status) status.textContent = text; connection.textContent = text; }
    async function request(path, options = {}) {
        const response = await fetch(base + path, {credentials: 'same-origin', cache: 'no-store', headers: {'Accept': 'application/json', ...options.headers}, ...options});
        if (response.status === 401 || response.status === 403) { stop(); note('Phiên hoặc quyền đã thay đổi. Vui lòng đăng nhập lại.'); }
        if (!response.ok) throw new Error('request failed');
        return response;
    }
    function link(text, path) { const a = document.createElement('a'); a.textContent = text; a.href = base + path; return a; }
    function render(inbox) {
        const list = center.querySelector('[data-notification-items]'); const fragment = document.createDocumentFragment();
        // Replace from authoritative API; Map also tolerates a repeated notification in a retried response.
        for (const item of new Map(inbox.items.map(n => [String(n.id), n])).values()) {
            if (!/^[1-9][0-9]*$/.test(String(item.id))) continue;
            const row = document.createElement('article'); row.className = 'list-group-item p-3' + (item.read ? '' : ' border-start border-primary border-4'); row.dataset.id = String(item.id);
            const title = document.createElement('h2'); title.className = 'h6 text-break'; title.textContent = item.title;
            const message = document.createElement('p'); message.className = 'text-break mb-2'; message.textContent = item.message;
            const time = document.createElement('time'); time.className = 'small text-secondary'; time.textContent = item.createdAt.replace('T', ' ') + ' UTC';
            const actions = document.createElement('div'); actions.className = 'd-flex flex-wrap gap-3 align-items-center mt-2'; actions.append(link('Mở mục liên quan', 'notifications/' + item.id + '/open'));
            if (!item.read) { const button = document.createElement('button'); button.type = 'button'; button.className = 'btn btn-sm btn-outline-secondary'; button.textContent = 'Đã đọc'; button.addEventListener('click', () => mark('api/notifications/' + item.id + '/read', button)); actions.append(button); }
            else { const read = document.createElement('span'); read.className = 'small text-secondary'; read.textContent = 'Đã đọc'; actions.append(read); }
            row.append(title, message, time, actions); fragment.append(row);
        }
        if (!fragment.childNodes.length) { const empty = document.createElement('p'); empty.className = 'alert alert-info'; empty.textContent = 'Chưa có thông báo trong mục này.'; fragment.append(empty); }
        list.replaceChildren(fragment);
        const pager = center.querySelector('[data-notification-pagination]'); const nodes = [];
        if (inbox.page > 0) nodes.push(link('Trang trước', 'notifications?page=' + (inbox.page - 1) + '&unread=' + center.dataset.unread));
        const label = document.createElement('span'); label.textContent = 'Trang ' + (inbox.page + 1) + ' · ' + inbox.totalElements + ' thông báo'; nodes.push(label);
        if (inbox.page + 1 < inbox.totalPages) nodes.push(link('Trang sau', 'notifications?page=' + (inbox.page + 1) + '&unread=' + center.dataset.unread));
        pager.replaceChildren(...nodes);
    }
    async function refresh() {
        if (stopped) return;
        if (loading) { dirty = true; return; }
        loading = true;
        try {
            const path = center ? 'api/notifications?page=' + center.dataset.page + '&size=20&unread=' + center.dataset.unread : 'api/notifications/unread-count';
            const data = await (await request(path)).json(); const count = center ? data.unreadCount : data.count;
            badges.forEach(b => { b.textContent = count > 99 ? '99+' : String(count); b.hidden = count === 0; });
            if (center) render(data);
            note('Đã đồng bộ thông báo. Thời gian UTC.');
        } catch (_) { if (!stopped) note('Chưa thể đồng bộ. Hệ thống sẽ thử lại.'); }
        finally { loading = false; if (dirty) { dirty = false; scheduleRefresh(); } }
    }
    function scheduleRefresh() { clearTimeout(refreshTimer); refreshTimer = setTimeout(refresh, 300); }
    async function mark(path, button) {
        if (!csrf || stopped) return;
        button.disabled = true;
        try { await request(path, {method: 'POST', headers: {'Accept': 'application/json', [csrf.headerName]: csrf.token}}); if (tabs) tabs.postMessage('refresh'); scheduleRefresh(); }
        catch (_) { note('Không thể đánh dấu đã đọc. Vui lòng tải lại trang.'); }
        finally { button.disabled = false; }
    }
    if (center) center.addEventListener('submit', e => {
        const form = e.target.closest('[data-notification-read]'); if (!form || !csrf) return;
        e.preventDefault(); const path = new URL(form.action).pathname.slice(base.length).replace(/^notifications/, 'api/notifications'); mark(path, form.querySelector('button'));
    });
    function cleanup() { clearInterval(heartbeat); clearTimeout(handshakeTimer); if (socket) { socket.onclose = null; socket.close(); socket = null; } }
    function stop() { stopped = true; clearTimeout(retryTimer); clearTimeout(refreshTimer); cleanup(); }
    function reconnect() {
        cleanup(); if (stopped) return;
        note('Kết nối tức thời gián đoạn; vẫn có thể xem dữ liệu đã lưu.');
        clearTimeout(retryTimer); retryTimer = setTimeout(connect, delay + Math.random() * 500); delay = Math.min(30000, delay * 2);
    }
    async function connect() {
        if (stopped) return;
        try {
            csrf = await (await request('api/notifications/csrf')).json();
            socket = new WebSocket(new URL(base + 'ws/notifications', location.href).href.replace(/^http/, 'ws'), ['v12.stomp']);
            buffer = ''; lastReceived = Date.now(); handshakeTimer = setTimeout(reconnect, 10000);
            socket.onopen = () => socket.send('CONNECT\naccept-version:1.2\nhost:' + location.host + '\nheart-beat:10000,10000\n' + csrf.headerName + ':' + csrf.token + '\n\n\0');
            socket.onmessage = event => {
                lastReceived = Date.now(); buffer += event.data;
                if (buffer.length > 32768) { reconnect(); return; }
                // Broker sends complete UTF-8 text frames, terminated by NUL; heartbeats are LF.
                buffer = buffer.replace(/^\n+/, '');
                let end;
                while ((end = buffer.indexOf('\0')) >= 0) {
                    const frame = buffer.slice(0, end); buffer = buffer.slice(end + 1).replace(/^\n+/, '');
                    const command = frame.split('\n', 1)[0].replace('\r', '');
                    if (command === 'CONNECTED') {
                        clearTimeout(handshakeTimer); delay = 1000; socket.send('SUBSCRIBE\nid:notifications\ndestination:/user/queue/notifications\nack:auto\n\n\0');
                        // Subscribe first, then reconcile DB: closes the initial-load/reconnect race.
                        refresh();
                        heartbeat = setInterval(() => { if (Date.now() - lastReceived > 35000) reconnect(); else if (socket && socket.readyState === WebSocket.OPEN) socket.send('\n'); }, 10000);
                    } else if (command === 'MESSAGE') scheduleRefresh();
                    else if (command === 'ERROR') { reconnect(); return; }
                }
            };
            socket.onclose = reconnect; socket.onerror = () => { if (socket) socket.close(); };
        } catch (_) { if (!stopped) reconnect(); }
    }
    if (tabs) tabs.onmessage = scheduleRefresh;
    window.addEventListener('focus', scheduleRefresh);
    window.addEventListener('pagehide', stop);
    window.addEventListener('pageshow', e => { if (e.persisted) { stopped = false; delay = 1000; refresh(); connect(); } });
    document.addEventListener('submit', e => { if (e.target.action && new URL(e.target.action).pathname === base + 'logout') stop(); });
    setInterval(() => { if (!document.hidden) refresh(); }, 60000);
    refresh(); connect();
})();
