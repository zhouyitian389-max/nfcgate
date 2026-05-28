import test from 'node:test';
import assert from 'node:assert/strict';

process.env.DATABASE_URL ??= 'postgresql://postgres:postgres@127.0.0.1:5432/nfcgate_test';

const { sequenceIsNext, setRoleSocket } = await import('../dist/services/relay.js');

function createSocket() {
  return {
    OPEN: 1,
    readyState: 1,
    closeCalls: [],
    close(code, reason) {
      this.closeCalls.push({ code, reason });
    }
  };
}

test('sequenceIsNext only accepts strictly increasing integer sequences', () => {
  assert.equal(sequenceIsNext(0, 1), true);
  assert.equal(sequenceIsNext(4, 4), false);
  assert.equal(sequenceIsNext(4, 3), false);
  assert.equal(sequenceIsNext(4, 4.5), false);
  assert.equal(sequenceIsNext(4, '5'), false);
});

test('setRoleSocket resets HCE command and response sequences on reconnect', () => {
  const previousHce = createSocket();
  const nextHce = createSocket();
  const session = {
    lastCommandSeq: 52,
    lastResponseSeq: 51,
    hce: previousHce,
    reader: undefined,
    external: undefined
  };

  setRoleSocket(session, 'hce', nextHce);

  assert.equal(session.hce, nextHce);
  assert.equal(session.lastCommandSeq, 0);
  assert.equal(session.lastResponseSeq, 0);
  assert.deepEqual(previousHce.closeCalls, [{ code: 1000, reason: 'Replaced by new hce connection' }]);
});

test('setRoleSocket resets only response sequence for reader/external reconnects', () => {
  const previousReader = createSocket();
  const nextReader = createSocket();
  const previousExternal = createSocket();
  const nextExternal = createSocket();
  const session = {
    lastCommandSeq: 12,
    lastResponseSeq: 11,
    hce: undefined,
    reader: previousReader,
    external: previousExternal
  };

  setRoleSocket(session, 'reader', nextReader);
  assert.equal(session.reader, nextReader);
  assert.equal(session.lastCommandSeq, 12);
  assert.equal(session.lastResponseSeq, 0);
  assert.deepEqual(previousReader.closeCalls, [{ code: 1000, reason: 'Replaced by new reader connection' }]);

  session.lastCommandSeq = 19;
  session.lastResponseSeq = 18;
  setRoleSocket(session, 'external', nextExternal);
  assert.equal(session.external, nextExternal);
  assert.equal(session.lastCommandSeq, 19);
  assert.equal(session.lastResponseSeq, 0);
  assert.deepEqual(previousExternal.closeCalls, [{ code: 1000, reason: 'Replaced by new external connection' }]);
});
