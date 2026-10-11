import assert from 'node:assert/strict';
import test from 'node:test';
import { nextTag, reserveTag } from './prerelease.mjs';

const date = '20261011';
const ref = suffix => ({ ref: `refs/tags/build-${date}-${suffix}` });

test('starts each UTC day at 01 and ignores legacy/unrelated tags', () => {
  assert.equal(nextTag([], date), 'build-20261011-01');
  assert.equal(nextTag([{ ref: 'refs/tags/build-20261010-99' }, { ref: 'refs/tags/build-9.1' }], date), 'build-20261011-01');
});

test('uses the numerical maximum, pads to two digits and permits 100+', () => {
  assert.equal(nextTag([ref('09'), ref('02'), ref('01')], date), 'build-20261011-10');
  assert.equal(nextTag([ref('99')], date), 'build-20261011-100');
  assert.equal(nextTag([ref('01')], date), 'build-20261011-02');
});

test('rejects invalid dates and unsafe counters', () => {
  assert.throws(() => nextTag([], '2026-10-11'), /YYYYMMDD/);
  assert.throws(() => nextTag([ref('9007199254740992')], date), /supported range/);
});

test('atomically reserves the tag for the exact built commit', () => {
  const calls = [];
  const api = (path, fields) => { calls.push({ path, fields }); return fields ? {} : []; };
  assert.equal(reserveTag(api, 'owner/repo', 'built-sha', date), 'build-20261011-01');
  assert.deepEqual(calls[1], {
    path: 'repos/owner/repo/git/refs', fields: { ref: 'refs/tags/build-20261011-01', sha: 'built-sha' },
  });
});

test('retries a competing reservation with the next number', () => {
  let occupied = false;
  const api = (path, fields) => {
    if (!fields) return occupied ? [ref('01')] : [];
    if (!occupied) { occupied = true; throw new Error('Ref already exists'); }
    assert.equal(fields.ref, 'refs/tags/build-20261011-02');
    return {};
  };
  assert.equal(reserveTag(api, 'owner/repo', 'built-sha', date), 'build-20261011-02');
});

test('does not mask credential failures as counter collisions', () => {
  const api = (path, fields) => { if (fields) throw new Error('HTTP 403'); return []; };
  assert.throws(() => reserveTag(api, 'owner/repo', 'built-sha', date), /HTTP 403/);
});
