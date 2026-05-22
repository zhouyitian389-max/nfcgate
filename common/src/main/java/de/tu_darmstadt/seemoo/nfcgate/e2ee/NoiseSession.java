package de.tu_darmstadt.seemoo.nfcgate.e2ee;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NoiseSession {
    private static final byte VERSION = 1;

    private final boolean initiator;
    private final NoiseIdentity identity;
    private final byte[] ephemeralPrivate = NoiseSupport.randomBytes(32);
    private final byte[] ephemeralPublic = NoiseSupport.derivePublicKey(ephemeralPrivate);

    private byte[] remoteEphemeral;
    private byte[] remoteStatic;
    private byte[] sendKey;
    private byte[] receiveKey;
    private long sendCounter;

    private NoiseSession(boolean initiator, NoiseIdentity identity) {
        this.initiator = initiator;
        this.identity = identity;
    }

    public static NoiseSession initiator(NoiseIdentity identity) {
        return new NoiseSession(true, identity);
    }

    public static NoiseSession responder(NoiseIdentity identity) {
        return new NoiseSession(false, identity);
    }

    public byte[] handshakeMessage1() {
        ensureInitiator();
        return pack(VERSION, ephemeralPublic);
    }

    public void readHandshakeMessage1(byte[] message) {
        ensureResponder();
        remoteEphemeral = unpack(message, 32);
    }

    public byte[] handshakeMessage2() {
        ensureResponder();
        return pack(VERSION, ephemeralPublic, identity.getStaticPublicKey());
    }

    public void readHandshakeMessage2(byte[] message) {
        ensureInitiator();
        byte[][] parts = unpackMany(message, 32, 32);
        remoteEphemeral = parts[0];
        remoteStatic = parts[1];
    }

    public byte[] handshakeMessage3() {
        ensureInitiator();
        byte[] transcript = buildTranscript(ephemeralPublic, remoteEphemeral, identity.getStaticPublicKey(), remoteStatic);
        byte[] finish = Arrays.copyOf(NoiseSupport.hmac(deriveHandshakeSecret(identity.getStaticPublicKey(), remoteStatic), transcript), 16);
        finalizeTransport(identity.getStaticPublicKey(), remoteStatic);
        return pack(VERSION, identity.getStaticPublicKey(), finish);
    }

    public void readHandshakeMessage3(byte[] message) {
        ensureResponder();
        byte[][] parts = unpackMany(message, 32, 16);
        remoteStatic = parts[0];
        byte[] expected = Arrays.copyOf(NoiseSupport.hmac(
                deriveHandshakeSecret(remoteStatic, identity.getStaticPublicKey()),
                buildTranscript(remoteEphemeral, ephemeralPublic, remoteStatic, identity.getStaticPublicKey())
        ), 16);
        if (!NoiseSupport.constantTimeEquals(expected, parts[1])) {
            throw new IllegalStateException("Noise handshake verification failed");
        }
        finalizeTransport(remoteStatic, identity.getStaticPublicKey());
    }

    public byte[] transportSend(byte[] plaintext) {
        ensureReady();
        return NoiseSupport.encrypt(sendKey, sendCounter++, plaintext);
    }

    public byte[] transportReceive(byte[] ciphertext) {
        ensureReady();
        return NoiseSupport.decrypt(receiveKey, ciphertext);
    }

    public byte[] getRemoteStaticPublicKey() {
        return remoteStatic != null ? remoteStatic.clone() : null;
    }

    private byte[] deriveHandshakeSecret(byte[] initiatorStatic, byte[] responderStatic) {
        return NoiseSupport.hkdf(NoiseSupport.concat(
                NoiseSupport.pseudoDh(ephemeralPrivate, remoteEphemeral),
                NoiseSupport.pseudoDh(identity.getStaticPrivateKey(), remoteEphemeral),
                NoiseSupport.pseudoDh(ephemeralPrivate, responderStatic),
                initiatorStatic,
                responderStatic
        ), "Noise_XX_25519_ChaChaPoly_BLAKE2s", 32);
    }

    private void finalizeTransport(byte[] initiatorStatic, byte[] responderStatic) {
        byte[] handshakeSecret = deriveHandshakeSecret(initiatorStatic, responderStatic);
        byte[] i2r = NoiseSupport.hkdf(handshakeSecret, "i2r", 32);
        byte[] r2i = NoiseSupport.hkdf(handshakeSecret, "r2i", 32);
        sendKey = initiator ? i2r : r2i;
        receiveKey = initiator ? r2i : i2r;
    }

    private byte[] buildTranscript(byte[] initiatorEphemeral, byte[] responderEphemeral, byte[] initiatorStatic, byte[] responderStatic) {
        return NoiseSupport.concat(initiatorEphemeral, responderEphemeral, initiatorStatic, responderStatic);
    }

    private void ensureReady() {
        if (sendKey == null || receiveKey == null) {
            throw new IllegalStateException("Handshake not complete");
        }
    }

    private void ensureInitiator() {
        if (!initiator) {
            throw new IllegalStateException("Responder cannot perform initiator operation");
        }
    }

    private void ensureResponder() {
        if (initiator) {
            throw new IllegalStateException("Initiator cannot perform responder operation");
        }
    }

    private static byte[] pack(byte version, byte[]... payloads) {
        int size = 1;
        for (byte[] payload : payloads) {
            size += payload.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(version);
        for (byte[] payload : payloads) {
            buffer.put(payload);
        }
        return buffer.array();
    }

    private static byte[] unpack(byte[] message, int expectedLength) {
        byte[][] payloads = unpackMany(message, expectedLength);
        return payloads[0];
    }

    private static byte[][] unpackMany(byte[] message, int... lengths) {
        if (message.length < 1 || message[0] != VERSION) {
            throw new IllegalArgumentException("Unsupported Noise message");
        }
        byte[][] payloads = new byte[lengths.length][];
        int offset = 1;
        for (int i = 0; i < lengths.length; i++) {
            int length = lengths[i];
            if (offset + length > message.length) {
                throw new IllegalArgumentException("Truncated Noise message");
            }
            payloads[i] = Arrays.copyOfRange(message, offset, offset + length);
            offset += length;
        }
        return payloads;
    }
}
