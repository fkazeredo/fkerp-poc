#!/usr/bin/env node
// PostToolUse(Edit|Write): run Spotless when a backend .java file changed (CLAUDE.md §15).
const { spawnSync } = require('child_process');
const path = require('path');

let raw = '';
process.stdin.on('data', (c) => (raw += c));
process.stdin.on('end', () => {
  let file = '';
  try {
    const input = JSON.parse(raw || '{}');
    file = (input.tool_input && input.tool_input.file_path) || '';
  } catch {
    process.exit(0);
  }
  if (!file.endsWith('.java')) process.exit(0);

  const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
  const backend = path.join(projectDir, 'backend');
  const isWin = process.platform === 'win32';
  const cmd = isWin ? 'mvnw.cmd' : './mvnw';

  spawnSync(cmd, ['-q', '-DskipTests', 'spotless:apply'], {
    cwd: backend,
    stdio: 'ignore',
    shell: isWin
  });
  process.exit(0); // formatting never blocks
});
