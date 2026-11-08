import selectorParser from 'postcss-selector-parser';

// Ordinary selectors keep the dashboard isolated without requiring CSS @scope.
export function homeCss() {
 return {
  postcssPlugin: 'studyontv-home-css',
  Once(root) {
   if (!root.source?.input.file?.replace(/\\/g, '/').endsWith('/@tabler/core/dist/css/tabler.css')) return;
   root.walkAtRules('charset', rule => rule.remove());
   root.walkRules(rule => {
    for (let parent = rule.parent; parent; parent = parent.parent) {
     if (parent.type === 'atrule' && /keyframes$/i.test(parent.name)) return;
    }
    rule.selector = selectorParser(selectors => {
     selectors.each(selector => {
      let anchored = false;
      for (const node of [...selector.nodes]) {
       if ((node.type === 'pseudo' && node.value === ':root') ||
           (node.type === 'tag' && ['html', 'body'].includes(node.value))) {
        node.replaceWith(selectorParser().astSync(':where(.tabler-home)').first.first.clone());
        anchored = true;
       }
      }
      if (!anchored) {
       selector.prepend(selectorParser.combinator({ value: ' ' }));
       selector.prepend(selectorParser().astSync(':where(.tabler-home)').first.first.clone());
      }
     });
    }).processSync(rule.selector);
   });
  },
 };
}
