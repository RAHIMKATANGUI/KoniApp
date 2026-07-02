package com.koni.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Animation fade-in logo
        ImageView logo = findViewById(R.id.splash_logo);
        TextView appName = findViewById(R.id.splash_name);
        TextView appSub = findViewById(R.id.splash_sub);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(900);
        fadeIn.setFillAfter(true);

        if (logo != null) logo.startAnimation(fadeIn);

        AlphaAnimation fadeIn2 = new AlphaAnimation(0f, 1f);
        fadeIn2.setDuration(900);
        fadeIn2.setStartOffset(400);
        fadeIn2.setFillAfter(true);
        if (appName != null) appName.startAnimation(fadeIn2);
        if (appSub != null) appSub.startAnimation(fadeIn2);

        // Lancer MainActivity apres 2.5 secondes
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 2500);
    }
}
