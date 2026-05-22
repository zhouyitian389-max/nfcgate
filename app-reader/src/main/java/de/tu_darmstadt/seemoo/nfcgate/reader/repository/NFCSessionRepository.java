package de.tu_darmstadt.seemoo.nfcgate.reader.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.reader.nfc.NFCEvent;

public class NFCSessionRepository {
    private final List<NFCEvent> events = new ArrayList<>();

    public synchronized void append(NFCEvent event) {
        events.add(event);
    }

    public synchronized List<NFCEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized void clear() {
        events.clear();
    }
}
