// Run separately: node src/test/js/operations.test.mjs (no npm dependencies).
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source=await readFile(new URL('../../main/resources/static/js/operations.js',import.meta.url),'utf8');
new vm.Script(source);
const handlers={},attributes=new Map(),loading={hidden:true};
const form={addEventListener:(event,fn)=>handlers[event]=fn,setAttribute:(key,value)=>attributes.set(key,value),removeAttribute:key=>attributes.delete(key)};
const window={addEventListener:(event,fn)=>handlers[event]=fn};
vm.runInNewContext(source,{window,document:{querySelector:selector=>selector==='[data-report-loading]'?loading:form}});
handlers.submit();assert.equal(loading.hidden,false);assert.equal(attributes.get('aria-busy'),'true');
handlers.pageshow();assert.equal(loading.hidden,true);assert.equal(attributes.has('aria-busy'),false);
assert.doesNotThrow(()=>vm.runInNewContext(source,{document:{querySelector:()=>null}}));
const passed=['report submit shows accessible loading state','back-forward restoration clears loading state','unrelated pages remain unaffected'];
console.log('Operations JavaScript: '+passed.length+' checks passed');
export {passed};
