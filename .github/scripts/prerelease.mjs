import { execFileSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function nextTag(refs, date) {
  if (!/^\d{8}$/.test(date)) throw new Error('Build date must be YYYYMMDD');
  const pattern = new RegExp(`^refs/tags/build-${date}-([0-9]{2,})$`);
  let sequence = 0;
  for (const { ref } of refs) {
    const match = pattern.exec(ref);
    if (!match) continue;
    const value = Number(match[1]);
    if (!Number.isSafeInteger(value) || value >= Number.MAX_SAFE_INTEGER) {
      throw new Error('Daily build sequence exceeds the supported range');
    }
    sequence = Math.max(sequence, value);
  }
  return `build-${date}-${String(sequence + 1).padStart(2, '0')}`;
}

// GitHub atomically creates refs: overlapping builds retry without overwriting a tag.
export function reserveTag(api, repository, sha, date) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const refs = api(`repos/${repository}/git/matching-refs/tags/build-${date}-`);
    const tag = nextTag(refs, date);
    try {
      api(`repos/${repository}/git/refs`, { ref: `refs/tags/${tag}`, sha });
      return tag;
    } catch (error) {
      // Retry only an actual name collision, not permissions/network/validation failures.
      const refreshed = api(`repos/${repository}/git/matching-refs/tags/${tag}`);
      if (!refreshed.some(({ ref }) => ref === `refs/tags/${tag}`)) throw error;
    }
  }
  throw new Error('Could not reserve a build tag after 20 concurrent collisions');
}

function runGh(args) {
  try {
    return execFileSync('gh', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  } catch (error) {
    throw new Error(`GitHub command failed: ${error.stderr?.toString().trim() || error.message}`);
  }
}

function jarsIn(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? jarsIn(path) : entry.name.endsWith('.jar') ? [path] : [];
  }).sort();
}

function main() {
  const [label, directory] = process.argv.slice(2);
  if (!label || !directory) throw new Error('Usage: prerelease.mjs LABEL ARTIFACT_DIRECTORY');
  const repository = process.env.GITHUB_REPOSITORY;
  const sha = process.env.GITHUB_SHA;
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository || '') || !/^[a-f0-9]{40}$/.test(sha || '')) {
    throw new Error('GITHUB_REPOSITORY and GITHUB_SHA are required');
  }
  const jars = jarsIn(resolve(directory));
  if (!jars.length) throw new Error('No tested JARs found');
  const api = (path, fields) => JSON.parse(runGh([
    'api', path,
    ...(fields ? ['--method', 'POST', ...Object.entries(fields).flatMap(([key, value]) => ['-f', `${key}=${value}`])] : []),
  ]));
  const date = new Date().toISOString().slice(0, 10).replaceAll('-', '');
  const tag = reserveTag(api, repository, sha, date);
  console.log(runGh([
    'release', 'create', tag, ...jars, '--repo', repository, '--verify-tag',
    '--title', `${label} ${tag}`, '--prerelease', '--latest=false', '--generate-notes',
  ]).trim());
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) main();
