package com.taqat.madar.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.RoleRouter;
import com.taqat.madar.data.local.SessionManager;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.onboarding.OnboardingActivity;

/**
 * شاشة البداية: تعرض الشعار لثوانٍ ثم تقرر الوجهة التالية.
 *
 * الجلسة هي Firebase Auth نفسها، لا مفتاح محلي: من هو مسجّل دخوله يُقرأ حسابه من
 * قاعدة البيانات ويُوجَّه لواجهة دوره أو شاشة المراجعة، ومن لا جلسة له يعود للجولة
 * التعريفية أول مرة ثم لشاشة الدخول بعدها.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000;

    private final Handler routeHandler = new Handler(Looper.getMainLooper());
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // الانتظار لمدة ثانيتين ثم الانتقال للشاشة التالية
        routeHandler.postDelayed(this::route, SPLASH_DELAY_MS);
    }

    /**
     * الشاشة تُغلق بمجرد أن تسبقها أخرى، فلا تبقى في سلسلة الرجوع. ولأنها تُغلق
     * أحياناً قبل أن يحين التوجيه، يُلغى النداء المعلّق: فتحُ وجه من نشاط مضى
     * يترك التاجر على شاشة لا يراها أو يكرّر الوجهة التي فتحناها للتو.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        routeHandler.removeCallbacksAndMessages(null);
    }

    private void route() {
        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            SessionManager sessionManager = new SessionManager(this);
            Class<?> destination = sessionManager.isOnboardingCompleted()
                    ? LoginActivity.class        // مستخدم قديم: تسجيل الدخول مباشرة
                    : OnboardingActivity.class;  // أول تشغيل: الجولة التعريفية
            startActivity(new Intent(this, destination));
            finish();
            return;
        }

        routeSession(account.getUid());
    }

    /** المستخدم داخل التطبيق أصلاً: نقرأ حالته ودوره ثم نفتح واجهته مباشرة */
    private void routeSession(String uid) {
        userRepository.loadUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onLoaded(User user) {
                // الوثيقة هي المصدر: هي من يحمل الدور وحالة الاعتماد وتأكيد البريد
                runOnUiThread(() -> RoleRouter.go(SplashActivity.this, user));
            }

            @Override
            public void onNotFound() {
                // جلسة بلا وثيقة مستخدم: لا يمكن توجيهه، فيعيد الدخول لتُقرأ بياناته
                runOnUiThread(SplashActivity.this::openLogin);
            }

            @Override
            public void onAccountGone() {
                // حساب حُذف من سيرفر الإدارة: الجلسة ميتة، فنجففها ولا نطيلها بشاشة دخول بغيرها
                runOnUiThread(() -> {
                    authRepository.signOut();
                    openLogin();
                });
            }

            @Override
            public void onError() {
                // تعذّرت القراءة للشبكة: ندع الجلسة كما هي ونفتح الدخول بلا أن نجرحها
                runOnUiThread(SplashActivity.this::openLogin);
            }
        });
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
