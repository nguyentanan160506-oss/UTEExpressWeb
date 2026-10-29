// No npm dependencies. Exercises responsive navigation and keyboard behavior.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source = await readFile(new URL('../../main/resources/static/js/ui.js', import.meta.url), 'utf8');
function fixture({mobile = true, target = true} = {}) {
    const handlers = {}, attrs = new Map([['aria-controls', 'menu']]);
    const media = {matches: mobile, addEventListener: (name, fn) => handlers.media = fn};
    const button = {hidden: true, focused: false, getAttribute: name => attrs.get(name), setAttribute: (k, v) => attrs.set(k, v), addEventListener: (name, fn) => handlers[name] = fn, focus: () => button.focused = true};
    const panel = {hidden: false, addEventListener: (name, fn) => handlers[name] = fn};
    const links = ['/', '/manager', '/manager/trips', '/manager/shipments'].map(path => ({href: 'http://localhost' + path, attrs: {}, setAttribute(k, v) { this.attrs[k] = v; }}));
    const document = {querySelector: () => null, getElementById: () => target ? panel : null, querySelectorAll: selector => selector === '[data-ui-menu]' ? [button] : [{querySelectorAll: () => links}]};
    vm.runInNewContext(source, {window: {matchMedia: () => media}, document, URL, location: {href: 'http://localhost/manager/trips/42', pathname: '/manager/trips/42'}});
    return {handlers, attrs, media, button, panel, links};
}
const passed = [];
const check = (name, fn) => { fn(); passed.push(name); };
check('mobile menu starts collapsed with an accessible toggle', () => { const f = fixture(); assert.equal(f.button.hidden, false); assert.equal(f.panel.hidden, true); assert.equal(f.attrs.get('aria-expanded'), 'false'); });
check('toggle opens and closes the controlled panel', () => { const f = fixture(); f.handlers.click(); assert.equal(f.panel.hidden, false); assert.equal(f.attrs.get('aria-expanded'), 'true'); f.handlers.click(); assert.equal(f.panel.hidden, true); });
check('Escape closes mobile menu and returns keyboard focus', () => { const f = fixture(); f.handlers.click(); f.handlers.keydown({key: 'Escape'}); assert.equal(f.panel.hidden, true); assert.equal(f.button.focused, true); });
check('desktop menu is visible without a redundant toggle', () => { const f = fixture({mobile: false}); assert.equal(f.panel.hidden, false); assert.equal(f.button.hidden, true); f.handlers.keydown({key: 'Escape'}); assert.equal(f.panel.hidden, false); });
check('viewport changes reset collapsed state safely', () => { const f = fixture(); f.handlers.click(); f.media.matches = false; f.handlers.media(); assert.equal(f.panel.hidden, false); f.media.matches = true; f.handlers.media(); assert.equal(f.panel.hidden, true); });
check('missing target does not break unrelated pages', () => assert.doesNotThrow(() => fixture({target: false})));
check('only the most specific route receives aria-current', () => { const f = fixture(); assert.equal(f.links.filter(l => l.attrs['aria-current'] === 'page').length, 1); assert.equal(f.links[2].attrs['aria-current'], 'page'); });
check('pages without navigation still run safely', () => assert.doesNotThrow(() => vm.runInNewContext(source, {window: {matchMedia: () => ({})}, document: {querySelector: () => null, querySelectorAll: () => []}})));
check('long detail index uses rendered text safely and preserves existing anchors', () => {
    const nodes = [];
    const headings = [{textContent: 'Thông tin', id: 'existing'}, {textContent: '<script>text only</script>', id: ''}, {textContent: 'Lịch sử', id: ''}];
    const title = {insertAdjacentElement: (position, node) => nodes.push(node)};
    const detail = {querySelectorAll: () => headings, querySelector: () => title};
    const document = {
        querySelector: () => detail, querySelectorAll: () => [], getElementById: () => null,
        createElement: tag => ({tag, children: [], setAttribute() {}, append(...children) { this.children.push(...children); }})
    };
    vm.runInNewContext(source, {window: {matchMedia: () => ({})}, document});
    assert.equal(nodes.length, 1);
    assert.equal(headings[0].id, 'existing');
    const links = nodes[0].children[1].children;
    assert.equal(links.length, 3);
    assert.equal(links[0].href, '#existing');
    assert.equal(links[1].textContent, '<script>text only</script>');
    assert.equal(new Set(headings.map(h => h.id)).size, 3);
});
console.log('UI JavaScript: ' + passed.length + ' checks passed');
export {passed};
