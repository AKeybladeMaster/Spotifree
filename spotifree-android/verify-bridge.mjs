import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const java = readFileSync(join(import.meta.dirname, 'src/com/spotifree/app/MainActivity.java'), 'utf8');
const block = java.match(/private static final String BRIDGE_SCRIPT =([\s\S]*?)\n\s*private final class Bridge/);
if (!block) throw new Error('BRIDGE_SCRIPT not found');
const literals = block[1].match(/"(?:\\.|[^"\\])*"/g) ?? [];
const script = literals.map(value => JSON.parse(value)).join('');
new Function(script);
if (!script.includes('window.__spotifreeControl') || !script.includes('SpotifreeBridge.state'))
  throw new Error('Bridge control/state hooks missing');
console.log(`Bridge JavaScript syntax valid (${script.length} characters)`);
