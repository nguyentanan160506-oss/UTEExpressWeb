// Run with Node 22+: node src/test/js/notifications.test.mjs (no npm dependencies).
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source = await readFile(new URL('../../main/resources/static/js/notifications.js', import.meta.url), 'utf8');
new vm.Script(source);
class Element {
    constructor(tag='span') { this.tagName=tag; this.dataset={}; this.childNodes=[]; this.handlers={}; this.hidden=false; this.disabled=false; this.textContent=''; }
    append(...items) { this.childNodes.push(...items); }
    replaceChildren(...items) { this.childNodes=items.flatMap(n=>n.tagName==='#fragment'?n.childNodes:[n]); }
    addEventListener(event,handler) { this.handlers[event]=handler; }
    querySelector(selector) { return this.selectors?.[selector] ?? null; }
}
function app({center=true,base='/ute/'}={}) {
    const badge=new Element(),connection=new Element(),list=new Element(),pager=new Element(),status=new Element();
    const widget=new Element(); widget.dataset.base=base; widget.selectors={'[data-notification-connection]':connection};
    const page=center?new Element():null;if(page){page.dataset={page:'0',unread:'false'};page.selectors={'[data-notification-items]':list,'[data-notification-pagination]':pager};}
    const doc={hidden:false,querySelector:s=>({'[data-notification-widget]':widget,'[data-notification-center]':page,'[data-notification-status]':status}[s]??null),querySelectorAll:()=>[badge],createElement:tag=>new Element(tag),createDocumentFragment:()=>new Element('#fragment'),addEventListener:()=>{}};
    const callbacks={},timers=new Map(),intervals=[],calls=[],sockets=[];let timerId=0,count=2;
    const item={id:1,title:'<script>unsafe</script>',message:'<img onerror=unsafe>',createdAt:'2026-10-23T09:00:00',read:false};
    const inbox={items:[item,item],page:0,size:20,totalElements:1,totalPages:1,unreadCount:count};
    class Socket {static OPEN=1;constructor(url,protocol){this.url=url;this.protocol=protocol;this.sent=[];this.readyState=1;sockets.push(this);}send(frame){this.sent.push(frame);}close(){this.readyState=3;}receive(frame){this.onmessage({data:frame});}}
    const window={addEventListener:(event,fn)=>{callbacks[event]=fn;}};
    const context=vm.createContext({window,document:doc,location:{href:'https://test.local/ute/notifications',host:'test.local'},URL,Map,WebSocket:Socket,Date,Math,
        setTimeout:(fn,ms)=>{timers.set(++timerId,{fn,ms});return timerId;},clearTimeout:id=>timers.delete(id),setInterval:(fn,ms)=>{intervals.push({fn,ms});return intervals.length;},clearInterval:()=>{},
        fetch:async(url,options)=>{calls.push({url,options});let data=url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'test-csrf'}:url.includes('/unread-count')?{count}:inbox;return {status:200,ok:true,json:async()=>data};}});
    vm.runInContext(source,context);
    return {badge,list,pager,page,status,calls,sockets,timers,intervals,callbacks,context,inbox,setCount:n=>{count=n;inbox.unreadCount=n;}};
}
async function settle(){for(let i=0;i<12;i++)await Promise.resolve();}
const passed=[];
{
    const a=app();await settle();assert.equal(a.badge.textContent,'2');assert.equal(a.list.childNodes.length,1);assert.equal(a.list.childNodes[0].childNodes[0].textContent,'<script>unsafe</script>');assert.equal(a.list.childNodes[0].childNodes[1].textContent,'<img onerror=unsafe>');assert.equal(a.sockets[0].url,'wss://test.local/ute/ws/notifications');
    assert.ok(a.calls.every(c=>c.options.credentials==='same-origin'&&c.options.cache==='no-store'));passed.push('badge, safe text rendering, dedup, context path and private fetch');
    const s=a.sockets[0];s.onopen();assert.ok(s.sent[0].includes('X-CSRF-TOKEN:test-csrf'));assert.ok(!s.sent[0].includes('userId'));
    s.receive('CONNECTED\nversion:1.2\n\n\0');await settle();assert.ok(s.sent[1].includes('destination:/user/queue/notifications'));assert.ok(!s.sent.some(f=>f.startsWith('SEND')));passed.push('CSRF CONNECT and receive-only private subscription');
    a.setCount(0);s.receive('MESSAGE\nsubscription:notifications\n\n{"type":"REFRESH"}\0');const refresh=[...a.timers.values()].find(t=>t.ms===300);assert.ok(refresh);refresh.fn();await settle();assert.equal(a.badge.hidden,true);assert.equal(a.list.childNodes.length,1);passed.push('realtime reconciliation replaces list and updates unread badge');
    const button=a.list.childNodes[0].childNodes[3].childNodes[1];await button.handlers.click();await settle();const mutation=a.calls.find(c=>c.options.method==='POST');assert.equal(mutation.url,'/ute/api/notifications/1/read');assert.equal(mutation.options.headers['X-CSRF-TOKEN'],'test-csrf');assert.ok(!mutation.options.body);passed.push('read action sends CSRF without client user/amount/content fields');
}
{
    const a=app({center:false});await settle();const s=a.sockets[0];s.onclose();let retry=[...a.timers.values()].find(t=>t.ms>=1000&&t.ms<1500);assert.ok(retry);retry.fn();await settle();a.sockets[1].onclose();retry=[...a.timers.values()].find(t=>t.ms>=2000&&t.ms<2500);assert.ok(retry);assert.ok(a.intervals.some(t=>t.ms===60000));passed.push('exponential retry and periodic database fallback');
    a.callbacks.pagehide();const before=a.sockets.length;a.callbacks.pageshow({persisted:true});await settle();assert.equal(a.sockets.length,before+1);passed.push('back-forward cache restores a stopped connection');
}
console.log('Notification JavaScript: '+passed.length+' checks passed');
for(const result of passed)console.log(' - '+result);
export {passed};
