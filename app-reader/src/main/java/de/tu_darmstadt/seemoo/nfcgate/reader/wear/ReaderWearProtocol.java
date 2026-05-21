package de.tu_darmstadt.seemoo.nfcgate.reader.wear;

public final class ReaderWearProtocol {
    private ReaderWearProtocol() {}

    public static final String CAPABILITY_READER = "nfcgate_reader";
    public static final String PATH_COMMAND = "/nfcgate/wear/command";
    public static final String PATH_STATUS = "/nfcgate/phone/status";
    public static final String PATH_TAP = "/nfcgate/phone/tap";
    public static final String PATH_DATA_SESSION = "/nfcgate/session";
}
