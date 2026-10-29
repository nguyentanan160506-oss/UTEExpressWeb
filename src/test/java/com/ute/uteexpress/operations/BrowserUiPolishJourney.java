package com.ute.uteexpress.operations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Presentation checks on the existing disposable H2/CDP harness, never a SQL endpoint. */
final class BrowserUiPolishJourney {
    private final BrowserQaTool b;
    BrowserUiPolishJourney(BrowserQaTool browser) { this.b = browser; }

    void run() throws Exception {
        b.call("Network.clearBrowserCookies", Map.of());
        pages("guest", List.of("/", "/tracking", "/quote", "/login", "/register", "/verify-email"));
        b.login("owner");
        pages("user", List.of("/account", "/account/profile", "/user/shipments", "/user/shipments/create", "/account/support", "/account/support/new", "/notifications"));
        if (b.createdShipment != null) pages("user-detail", List.of("/user/shipments/" + b.createdShipment));
        linked("user-support", "/account/support", "/account/support/");
        b.login("manager");
        pages("manager", List.of("/manager", "/manager/operations", "/manager/shipments", "/manager/handoffs", "/manager/trips", "/manager/trips/new", "/manager/trips/local-ready", "/manager/deliveries", "/manager/tickets", "/manager/tickets/new"));
        linked("manager-detail", "/manager/shipments", "/manager/shipments/");
        linked("transport-detail", "/manager/trips", "/manager/trips/");
        b.login("destination-manager");
        linked("ticket-detail", "/manager/tickets", "/manager/tickets/");
        linked("delivery-manager-detail", "/manager/deliveries", "/manager/deliveries/");
        b.login("pickup-courier");
        pages("shipper", List.of("/shipper", "/shipper/pickups", "/shipper/deliveries"));
        linked("pickup-detail", "/shipper/pickups", "/shipper/pickups/");
        b.login("shipper");
        linked("delivery-shipper-detail", "/shipper/deliveries", "/shipper/deliveries/");
        b.login("admin");
        pages("admin", List.of("/admin", "/admin/users", "/admin/services", "/admin/services/new", "/admin/pricing", "/admin/pricing/new?serviceId=1", "/admin/pricing/preview", "/admin/reports", "/admin/audit"));
        linked("admin-user-detail", "/admin/users", "/admin/users/");
        linked("admin-service-detail", "/admin/services", "/admin/services/");
        linked("admin-price-detail", "/admin/pricing", "/admin/pricing/");
    }

    private void linked(String area, String list, String prefix) throws Exception {
        b.nav(list);
        String path = b.js("(()=>{const prefix=" + BrowserQaTool.q(prefix) + ";return [...document.querySelectorAll('main a[href]')].map(a=>a.getAttribute('href')).find(h=>h.startsWith(prefix)&&/^[0-9]+$/.test(h.slice(prefix.length)))||'';})()").asText();
        if (path.isEmpty()) b.blocked("UI " + area, "No owned detail link remains in isolated fixture at " + list);
        else pages(area, List.of(path));
    }

    private void pages(String area, List<String> paths) throws Exception {
        for (String path : paths) {
            for (int width : List.of(1440, 768, 390, 320)) {
                b.call("Emulation.setDeviceMetricsOverride", Map.of("width", width, "height", 900, "deviceScaleFactor", 1, "mobile", width < 576));
                b.nav(path);
                var state = b.js("(()=>{const main=document.querySelector('main');const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);const forms=[...document.querySelectorAll('form[method=post]')];const unlabelled=[...document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]),select,textarea')].filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')&&!e.getAttribute('aria-labelledby')).map(e=>e.name);const needsIndex=main?.hasAttribute('data-ui-sections')&&[...main.querySelectorAll('h2')].filter(h=>h.textContent.trim()).length>=3;return {http:performance.getEntriesByType('navigation')[0].responseStatus,overflow:document.documentElement.scrollWidth>innerWidth+1,main:!!main?.id,unique:new Set(ids).size===ids.length,css:getComputedStyle(document.documentElement).getPropertyValue('--ute-navy').trim(),csrf:forms.every(f=>!!f.querySelector('input[name=_csrf]')),widgets:document.querySelectorAll('[data-notification-widget]').length,unlabelled,sectionIndex:!needsIndex||!!main.querySelector('.section-index a')};})()");
                b.result("UI " + area + " " + path + " @" + width,
                    state.path("http").asInt() == 200 && !state.path("overflow").asBoolean()
                        && state.path("main").asBoolean() && state.path("unique").asBoolean()
                        && state.path("csrf").asBoolean() && state.path("widgets").asInt() <= 1
                        && state.path("unlabelled").isEmpty() && state.path("sectionIndex").asBoolean()
                        && state.path("css").asText().equals("#142b49"), state.toString());
                if (width == 390) keyboardMenu(area + " " + path);
                if ((width == 1440 || width == 390) && (paths.indexOf(path) == 0 || path.equals("/user/shipments/create") || path.equals("/admin/reports") || path.equals("/manager/operations"))) {
                    screenshot(area + (paths.indexOf(path) == 0 ? "" : "-" + path.substring(1).replace('/', '-')), width);
                }
            }
        }
    }

    private void keyboardMenu(String page) throws Exception {
        if (!b.js("!!document.querySelector('[data-ui-menu]')").asBoolean()) return;
        var result = b.js("(()=>{const toggle=document.querySelector('[data-ui-menu]'),panel=document.getElementById(toggle.getAttribute('aria-controls'));if(toggle.hidden||!panel.hidden)return false;toggle.click();if(panel.hidden||toggle.getAttribute('aria-expanded')!=='true')return false;const link=panel.querySelector('a');link?.focus();panel.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return panel.hidden&&toggle.getAttribute('aria-expanded')==='false'&&document.activeElement===toggle;})()");
        b.result("Mobile menu keyboard " + page, result.asBoolean(), "Expand, Escape, focus return; no route mutation");
    }

    private void screenshot(String area, int width) throws Exception {
        var shot = b.call("Page.captureScreenshot", Map.of("format", "png", "captureBeyondViewport", false));
        Path dir = Path.of(System.getenv().getOrDefault("UTE_BROWSER_EVIDENCE_DIR", System.getProperty("java.io.tmpdir")));
        Files.createDirectories(dir);
        Files.write(dir.resolve("uteexpress-ui-" + area + "-" + width + ".png"), Base64.getDecoder().decode(shot.path("data").asText()));
    }
}
