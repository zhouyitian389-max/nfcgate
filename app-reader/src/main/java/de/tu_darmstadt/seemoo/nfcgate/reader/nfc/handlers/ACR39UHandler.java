package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.handlers;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCHandler;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCSource;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv.EMVReader;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.emv.TLVParser;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.CCIDTransport;
import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport.USBConnection;

public class ACR39UHandler implements NFCHandler {
    private static final String TAG = "ACR39UHandler";
    private static final byte[] SELECT_PPSE = {
            0x00, (byte) 0xA4, 0x04, 0x00, 0x0E,
            '2', 'P', 'A', 'Y', '.', 'S', 'Y', 'S', '.', 'D', 'D', 'F', '0', '1',
            0x00
    };

    private final USBConnection usbConnection;
    private final CCIDTransport ccidTransport;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile UsbDevice targetDevice;

    public ACR39UHandler(USBConnection usbConnection, CCIDTransport ccidTransport) {
        this.usbConnection = usbConnection;
        this.ccidTransport = ccidTransport;
    }

    public void setTargetDevice(UsbDevice targetDevice) {
        this.targetDevice = targetDevice;
    }

    @Override
    public void startCapture(EventCallback callback) {
        UsbDevice device = targetDevice;
        if (device == null) {
            callback.onEvent(new NFCEvent.Error("No ACR39U USB device selected", NFCSource.ACR39U));
            return;
        }
        if (!usbConnection.connect(device)) {
            callback.onEvent(new NFCEvent.Error("Cannot open ACR39U USB connection", NFCSource.ACR39U));
            return;
        }
        if (!ccidTransport.configureForDevice(device)) {
            usbConnection.disconnect();
            callback.onEvent(new NFCEvent.Error("Cannot configure ACR39U CCID endpoints", NFCSource.ACR39U));
            return;
        }
        capturing.set(true);
        worker.execute(() -> {
            try {
                while (capturing.get()) {
                    byte[] atr = ccidTransport.powerOn();
                    if (atr.length == 0) {
                        sleepQuietly(600);
                        continue;
                    }
                    String protocol = detectProtocolFromAtr(atr);
                    EMVReader.EMVCard card = readEmvCard();
                    if (card != null && card.pan != null) {
                        String type = card.brand != null ? card.brand : protocol;
                        callback.onEvent(new NFCEvent.CardDetected(
                                type,
                                atr,
                                NFCSource.ACR39U,
                                System.currentTimeMillis(),
                                card
                        ));
                        sleepQuietly(1200);
                    } else {
                        sleepQuietly(600);
                    }
                }
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "Unknown ACR39U error";
                Log.w(TAG, "Capture failed", e);
                callback.onEvent(new NFCEvent.Error(message, NFCSource.ACR39U));
            } finally {
                usbConnection.disconnect();
            }
        });
    }

    @Override
    public void stopCapture() {
        capturing.set(false);
        usbConnection.disconnect();
    }

    private EMVReader.EMVCard readEmvCard() {
        byte[] ppseResponse = ccidTransport.xfrBlock(SELECT_PPSE);
        if (!isSuccess(ppseResponse)) {
            return null;
        }

        List<String> candidateAids = parseAids(stripStatus(ppseResponse));
        for (String aid : candidateAids) {
            byte[] aidResponse = ccidTransport.xfrBlock(buildSelectAid(aid));
            if (!isSuccess(aidResponse)) {
                continue;
            }
            byte[] pdol = parseTag(stripStatus(aidResponse), 0x9F38);
            byte[] gpoResponse = ccidTransport.xfrBlock(buildGpo(pdol));
            if (!isSuccess(gpoResponse)) {
                continue;
            }

            EMVReader.EMVCard card = new EMVReader.EMVCard();
            card.aid = aid;
            card.brand = getBrandFromAid(aid);
            byte[] afl = parseAfl(stripStatus(gpoResponse));
            if (afl != null && afl.length % 4 == 0) {
                readRecordsFromAfl(afl, card);
            }
            if (card.pan != null) {
                return card;
            }
        }
        return null;
    }

    private void readRecordsFromAfl(byte[] afl, EMVReader.EMVCard card) {
        for (int i = 0; i < afl.length - 3; i += 4) {
            int sfi = (afl[i] >> 3) & 0x1F;
            int firstRecord = afl[i + 1] & 0xFF;
            int lastRecord = afl[i + 2] & 0xFF;
            for (int rec = firstRecord; rec <= lastRecord; rec++) {
                byte[] readRecord = {0x00, (byte) 0xB2, (byte) rec, (byte) ((sfi << 3) | 0x04), 0x00};
                byte[] response = ccidTransport.xfrBlock(readRecord);
                if (isSuccess(response)) {
                    parseRecord(stripStatus(response), card);
                }
            }
        }
    }

    private static void parseRecord(byte[] record, EMVReader.EMVCard card) {
        TLVParser parser = new TLVParser(record);

        if (card.pan == null) {
            byte[] panBytes = parser.find(0x5A);
            if (panBytes != null) {
                card.pan = bytesToHex(panBytes).replaceAll("F+$", "");
            }
        }
        if (card.track2 == null) {
            byte[] track2 = parser.find(0x57);
            if (track2 != null) {
                String t2 = bytesToHex(track2).replaceAll("F+$", "");
                card.track2 = t2;
                int sep = t2.indexOf('D');
                if (card.pan == null) {
                    card.pan = sep >= 0 ? t2.substring(0, sep) : t2;
                }
                if (card.expiry == null && sep >= 0 && sep + 5 <= t2.length()) {
                    card.expiry = t2.substring(sep + 1, sep + 5);
                }
            }
        }
        if (card.expiry == null) {
            byte[] expiry = parser.find(0x5F24);
            if (expiry != null) {
                String hex = bytesToHex(expiry);
                card.expiry = hex.length() >= 4 ? hex.substring(0, 4) : hex;
            }
        }
        if (card.cardholderName == null) {
            byte[] name = parser.find(0x5F20);
            if (name != null) {
                card.cardholderName = new String(name, StandardCharsets.UTF_8).trim();
            }
        }
    }

    private static boolean isSuccess(byte[] response) {
        return response != null
                && response.length >= 2
                && response[response.length - 2] == (byte) 0x90
                && response[response.length - 1] == (byte) 0x00;
    }

    private static byte[] stripStatus(byte[] response) {
        if (response == null || response.length < 2) {
            return new byte[0];
        }
        byte[] data = new byte[response.length - 2];
        System.arraycopy(response, 0, data, 0, data.length);
        return data;
    }

    private static List<String> parseAids(byte[] fci) {
        List<byte[]> values = new TLVParser(fci).findAll(0x4F);
        List<String> aids = new ArrayList<>(values.size());
        for (byte[] value : values) {
            String aid = bytesToHex(value);
            if (!aid.isEmpty()) {
                aids.add(aid);
            }
        }
        return aids;
    }

    private static byte[] parseTag(byte[] data, int tag) {
        return new TLVParser(data).find(tag);
    }

    private static byte[] parseAfl(byte[] gpoData) {
        byte[] afl = new TLVParser(gpoData).find(0x94);
        if (afl != null) {
            return afl;
        }
        if (gpoData.length >= 4 && (gpoData[0] & 0xFF) == 0x80) {
            int len = gpoData[1] & 0xFF;
            if (len >= 2 && gpoData.length >= 2 + len) {
                int aflLength = len - 2;
                if (aflLength > 0 && gpoData.length >= 4 + aflLength) {
                    byte[] out = new byte[aflLength];
                    System.arraycopy(gpoData, 4, out, 0, aflLength);
                    return out;
                }
            }
        }
        return null;
    }

    private static byte[] buildSelectAid(String aidHex) {
        byte[] aidBytes = hexToBytes(aidHex);
        byte[] command = new byte[5 + aidBytes.length + 1];
        command[0] = 0x00;
        command[1] = (byte) 0xA4;
        command[2] = 0x04;
        command[3] = 0x00;
        command[4] = (byte) aidBytes.length;
        System.arraycopy(aidBytes, 0, command, 5, aidBytes.length);
        command[command.length - 1] = 0x00;
        return command;
    }

    private static byte[] buildGpo(byte[] pdol) {
        byte[] pdolData = buildPdolData(pdol);
        int commandDataLength = 2 + pdolData.length;
        byte[] command = new byte[5 + commandDataLength + 1];
        command[0] = (byte) 0x80;
        command[1] = (byte) 0xA8;
        command[2] = 0x00;
        command[3] = 0x00;
        command[4] = (byte) commandDataLength;
        command[5] = (byte) 0x83;
        command[6] = (byte) pdolData.length;
        if (pdolData.length > 0) {
            System.arraycopy(pdolData, 0, command, 7, pdolData.length);
        }
        command[command.length - 1] = 0x00;
        return command;
    }

    private static byte[] buildPdolData(byte[] pdol) {
        if (pdol == null || pdol.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < pdol.length) {
            int first = pdol[offset++] & 0xFF;
            if ((first & 0x1F) == 0x1F) {
                while (offset < pdol.length) {
                    int next = pdol[offset++] & 0xFF;
                    if ((next & 0x80) == 0) {
                        break;
                    }
                }
            }
            if (offset >= pdol.length) {
                break;
            }
            int fieldLength = pdol[offset++] & 0xFF;
            for (int i = 0; i < fieldLength; i++) {
                out.write(0x00);
            }
        }
        return out.toByteArray();
    }

    private static String detectProtocolFromAtr(byte[] atr) {
        if (atr == null || atr.length < 2) {
            return "T=0";
        }
        int offset = 1;
        int y = (atr[offset] >> 4) & 0x0F;
        int protocol = 0;
        offset++;
        while (offset < atr.length) {
            if ((y & 0x1) != 0) offset++;
            if ((y & 0x2) != 0) offset++;
            if ((y & 0x4) != 0) offset++;
            if ((y & 0x8) != 0 && offset < atr.length) {
                int tdi = atr[offset++] & 0xFF;
                protocol = tdi & 0x0F;
                y = (tdi >> 4) & 0x0F;
            } else {
                break;
            }
        }
        return protocol == 0x01 ? "T=1" : "T=0";
    }

    private static String getBrandFromAid(String aid) {
        if (aid == null) return "UNKNOWN";
        String upper = aid.toUpperCase();
        if (upper.startsWith("A0000000031010")) return "Visa";
        if (upper.startsWith("A0000000041010")) return "Mastercard";
        if (upper.startsWith("A000000333")) return "UnionPay";
        if (upper.startsWith("A0000000651010")) return "JCB";
        if (upper.startsWith("A000000152")) return "Discover";
        return "UNKNOWN";
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len - 1; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
