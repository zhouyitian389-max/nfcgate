import test from 'node:test';
import assert from 'node:assert/strict';

const { decryptString, encryptString } = await import('../dist/utils/crypto.js');
const { isStrongPassword, isValidEmail, normalizeTrack2 } = await import('../dist/utils/validators.js');

test('encryptString stores card data encrypted and decrypts losslessly', () => {
  const plaintext = '4111111111111111';
  const encrypted = encryptString(plaintext);

  assert.notEqual(encrypted, plaintext);
  assert.equal(decryptString(encrypted), plaintext);
});

test('registration validators enforce email format and password strength', () => {
  assert.equal(isValidEmail('user@example.com'), true);
  assert.equal(isValidEmail('not-an-email'), false);
  assert.equal(isStrongPassword('Weakpass'), false);
  assert.equal(isStrongPassword('StrongPass1!'), true);
  const maxLengthPassword = `A1!${'a'.repeat(253)}`;
  assert.equal(maxLengthPassword.length, 256);
  assert.equal(isStrongPassword(maxLengthPassword), true);
  assert.equal(isStrongPassword(`${maxLengthPassword}a`), false);
});

test('track2 values are normalized to uppercase before validation', () => {
  assert.equal(normalizeTrack2('1234d5678f'), '1234D5678F');
  assert.equal(normalizeTrack2('invalid-z'), undefined);
});
