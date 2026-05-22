package de.tu_darmstadt.seemoo.nfcgate.hce.hce;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import de.tu_darmstadt.seemoo.nfcgate.hce.session.HceSessionStore;

public class NfcgateHostApduService extends HostApduService {
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        HceSessionStore store = new HceSessionStore(this);
        ReplayEngine engine = new ReplayEngine(store.getActive());
        return engine.nextResponse(commandApdu);
    }

    @Override
    public void onDeactivated(int reason) {
    }
}
