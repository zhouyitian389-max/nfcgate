package de.tu_darmstadt.seemoo.nfcgate.hce.cloud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CloudEventSourceTest {
    @Test
    public void extractEventTypeSupportsTypeField() throws Exception {
        assertEquals("new_cards", CloudEventSource.extractEventType("{\"type\":\"new_cards\"}"));
    }

    @Test
    public void extractEventTypeSupportsEventField() throws Exception {
        assertEquals("logout", CloudEventSource.extractEventType("{\"event\":\"logout\"}"));
    }

    @Test
    public void extractEventTypeSupportsBooleanFlags() throws Exception {
        assertEquals("new_cards", CloudEventSource.extractEventType("{\"new_cards\":true}"));
    }
}
