#!/usr/bin/env node
// PreToolUse(Edit|Write): block edits to generated / build-output paths (CLAUDE.md §15).
const GENERATED = /(^|[\\/])(target|generated|generated-sources|node_modules|dist|\.angular)([\\/]|$)/;

let raw = '';
process.stdin.on('data', (c) => (raw += c));
process.stdin.on('end', () => {
  let filePath = '';
  try {
    const input = JSON.parse(raw || '{}');
    filePath = (input.tool_input && (input.tool_input.file_path || input.tool_input.path)) || '';
  } catch {
    process.exit(0); // fail open on malformed input
  }
  if (filePath && GENERATED.test(filePath.replace(/\\/g, '/'))) {
    console.error(`Blocked: "${filePath}" is a generated/build path and must not be hand-edited. ` +
      `Change the generation source instead.`);
    process.exit(2); // exit 2 => block the tool call
  }
  process.exit(0);
});
