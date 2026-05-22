package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import de.tu_darmstadt.seemoo.nfcgate.hce.server.ReceiveServer;
import de.tu_darmstadt.seemoo.nfcgate.hce.server.TokenManager;
import de.tu_darmstadt.seemoo.nfcgate.hce.session.HceSessionStore;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        HceSessionStore store = new HceSessionStore(this);
        ReceiveServer server = new ReceiveServer(this);
        TokenManager tokenManager = new TokenManager(this);

        TextView summary = findViewById(R.id.hce_summary);
        summary.setText(getString(
                R.string.hce_summary,
                store.list().size(),
                server.getFingerprint(),
                tokenManager.getToken()
        ));
    }
}
