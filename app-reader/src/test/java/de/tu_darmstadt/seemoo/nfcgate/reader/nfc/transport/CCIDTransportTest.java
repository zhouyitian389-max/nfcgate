package de.tu_darmstadt.seemoo.nfcgate.reader.nfc.transport;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CCIDTransportTest {
    @Test
    public void buildPowerOnCommandHasExpectedHeader() {
        byte[] command = CCIDTransport.buildPowerOnCommand(0x12);
        assertEquals(10, command.length);
        assertEquals(0x62, command[0] & 0xFF);
        assertEquals(0x00, command[5] & 0xFF);
        assertEquals(0x12, command[6] & 0xFF);
        assertEquals(0x00, command[7] & 0xFF);
    }

    @Test
    public void buildXfrBlockCommandEncodesLengthAndPayload() {
        byte[] apdu = new byte[]{0x00, (byte) 0xA4, 0x04, 0x00};
        byte[] command = CCIDTransport.buildXfrBlockCommand(0x34, apdu);
        assertEquals(14, command.length);
        assertEquals(0x6F, command[0] & 0xFF);
        assertEquals(4, CCIDTransport.readUInt32LE(command, 1));
        assertEquals(0x00, command[5] & 0xFF);
        assertEquals(0x34, command[6] & 0xFF);
        assertArrayEquals(apdu, new byte[]{command[10], command[11], command[12], command[13]});
    }
}
