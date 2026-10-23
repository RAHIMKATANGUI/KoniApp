package com.koni.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMain = () -> {
        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        fadeIn(findViewById(R.id.splash_logo), 0);
        fadeIn(findViewById(R.id.splash_name), 400);
        fadeIn(findViewById(R.id.splash_sub), 400);
        fadeIn(findViewById(R.id.splash_editor), 700);

        handler.postDelayed(openMain, 2500);
    }

    private static void fadeIn(View v, long delay) {
        if (v == null) return;
        // Les vues démarrent à alpha=0 dans le XML : on les rend visibles puis on anime
        v.setAlpha(1f);
        AlphaAnimation a = new AlphaAnimation(0f, 1f);
        a.setDuration(900);
        a.setStartOffset(delay);
        a.setFillBefore(true);
        a.setFillAfter(true);
        v.startAnimation(a);
    }

    @Override
    protected void onDestroy() {
        // Si l'utilisateur quitte pendant le splash, ne pas relancer l'app
        handler.removeCallbacks(openMain);
        super.onDestroy();
    }
}
