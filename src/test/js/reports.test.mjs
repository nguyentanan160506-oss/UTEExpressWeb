// node src/test/js/reports.test.mjs — no npm dependencies.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
const source=await readFile(new URL('../../main/resources/static/js/reports.js',import.meta.url),'utf8');
new vm.Script(source);
const handlers={},status={value:'DELIVERED'};let submits=0;
const dataset={addEventListener:(type,fn)=>handlers[type]=fn};
const form={querySelector:s=>s==='[data-report-dataset]'?dataset:status,requestSubmit:()=>submits++};
vm.runInNewContext(source,{document:{querySelector:()=>form}});handlers.change();assert.equal(status.value,'');assert.equal(submits,1);
assert.doesNotThrow(()=>vm.runInNewContext(source,{document:{querySelector:()=>null}}));
assert.doesNotThrow(()=>vm.runInNewContext(source,{document:{querySelector:()=>({querySelector:()=>null})}}));
const passed=['dataset switch clears incompatible status and submits server filter','unrelated pages unaffected','audit form without dataset remains unaffected'];
console.log('Reports JavaScript: '+passed.length+' checks passed');export {passed};
