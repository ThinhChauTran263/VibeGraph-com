const fs = require('fs');
const file = 'd:/Users/User/IdeaProjects/VibeGraph/vibegraph-cli/bin/vibegraph.js';
let code = fs.readFileSync(file, 'utf8');

code = code.replace(/  let processing = false;[\s\S]*?process\.stdin\.prependListener\(\"keypress\", refreshSuggestions\);\r?\n/, '');
code = code.replace(/      processing = true;\r?\n      keypressVersion \+= 1;\r?\n      selectedSuggestionIndex = -1;\r?\n      visibleSuggestions = \[\];\r?\n      clearLiveSuggestions\(\);\r?\n/, '');
code = code.replace(/        processing = false;\r?\n/, '');
code = code.replace(/  emitKeypressEvents\(process\.stdin, readline\);\r?\n/, '');

fs.writeFileSync(file, code);
console.log('Fixed CLI suggestions!');
