package de.tu_darmstadt.seemoo.nfcgate.hce;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setTitle(R.string.title_about);
        TextView tvVersion = findViewById(R.id.tv_version);
        tvVersion.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));
        MaterialButton btn = findViewById(R.id.btn_open_github);
        btn.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.about_github)))));
    }
}
