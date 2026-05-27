import test from 'node:test';
import assert from 'node:assert/strict';

const { validateManagedUserInput } = await import('../dist/services/adminUsers.js');

test('validateManagedUserInput accepts strong admin-created user payloads', () => {
  const result = validateManagedUserInput({
    email: 'NewUser@example.com',
    password: 'StrongPass1!',
    name: 'Reader Device',
    accountName: 'Account A'
  });

  assert.equal(result.ok, true);
  assert.deepEqual(result.value, {
    email: 'newuser@example.com',
    password: 'StrongPass1!',
    name: 'Reader Device',
    accountName: 'Account A',
    role: 'USER'
  });
});

test('validateManagedUserInput rejects weak passwords and invalid emails', () => {
  const badEmail = validateManagedUserInput({
    email: 'nope',
    password: 'StrongPass1!'
  });
  assert.equal(badEmail.ok, false);
  assert.match(badEmail.message, /valid email/i);

  const badPassword = validateManagedUserInput({
    email: 'user@example.com',
    password: 'weakpass'
  });
  assert.equal(badPassword.ok, false);
  assert.match(badPassword.message, /Password must/i);
});
