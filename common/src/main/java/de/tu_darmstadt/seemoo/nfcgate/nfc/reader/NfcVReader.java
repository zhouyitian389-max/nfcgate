package de.tu_darmstadt.seemoo.nfcgate.nfc.reader;

import android.nfc.Tag;
import android.nfc.tech.NfcV;

import de.tu_darmstadt.seemoo.nfcgate.nfc.config.ConfigBuilder;

/**
 * Implements an NFCTagReader using the NfcV technology
 */
public class NfcVReader extends NFCTagReader {
    /**
     * Provides a NFC reader interface
     *
     * @param tag: A tag using the NfcV technology.
     */
    NfcVReader(Tag tag) {
        super(NfcV.get(tag));
    }

    @Override
    public ConfigBuilder getConfig() {
        // NfcV (ISO 15693) emulation is not supported: Android HCE only supports ISO-DEP
        // (ISO 14443-4), and the NCI stack has no Listen-V configuration parameters.
        return new ConfigBuilder();
    }
}
