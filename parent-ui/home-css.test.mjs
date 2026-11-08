import assert from 'node:assert/strict';
import test from 'node:test';
import postcss from 'postcss';
import {homeCss} from './home-css.mjs';

test('home styles isolate resets, selector lists and media rules while preserving animation steps', async () => {
 const css = ':root{--color:red}body{margin:0}.card,:is(.btn,.link){color:red}@media(max-width:767px){.row{display:flex}}@keyframes spin{from{opacity:0}50%,to{opacity:1}}';
 const {root} = await postcss([homeCss()]).process(css, {from:'/node_modules/@tabler/core/dist/css/tabler.css'});
 const selectors=[];
 root.walkRules(rule=>selectors.push(rule.selector));
 assert.deepEqual(selectors, [':where(.tabler-home)', ':where(.tabler-home)', ':where(.tabler-home) .card,:where(.tabler-home) :is(.btn,.link)', ':where(.tabler-home) .row', 'from', '50%,to']);
});

test('other pages retain their original CSS', async () => {
 const css = 'body{margin:0}.card{color:red}';
 const result = await postcss([homeCss()]).process(css, {from:'/src/styles.css'});
 assert.equal(result.css, css);
});
