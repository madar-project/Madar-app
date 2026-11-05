package com.taqat.madar.ui.auth.email;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.ui.RoleRouter;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthError;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.ActivityEmailVerificationBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;

/**
 * بوابة تأكيد البريد: لا يدخل المستخدم التطبيق قبل الضغط على الرابط المرسل لبريده،
 * لأن البريد هو هوية الحساب ومكان استرجاع كلمة المرور، فيجب أن يكون لصاحبه فعلاً.
 */
public class EmailVerificationActivity extends AppCompatActivity {

    /**
     * المهلة الإجبارية بين إرسال ورابطين. فير بيزيز تحظر الجهاز كله حين تتكرر
     * طلبات البريد، فكل ضغطة جديدة تُعيد المؤقّت من الصفر وتُطوّل الحظر.
     */
    private static final long RESEND_COOLDOWN_MS = 60_000L;

    private ActivityEmailVerificationBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();

    private final Handler countdown = new Handler(Looper.getMainLooper());
    private long cooldownEndsAt;
    private boolean checking;
    private boolean linkSent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmailVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        if (authRepository.currentUser() == null) {
            signOut();
            return;
        }

        binding.tvVerifyEmail.setText(authRepository.currentUserEmail());
        binding.btnIVerified.setOnClickListener(v -> checkVerification(true));
        binding.btnResendLink.setOnClickListener(v -> sendLink());
        binding.btnVerifySignOut.setOnClickListener(v -> signOut());
    }

    /** المستخدم راجع من تطبيق البريد: نفحص الحالة بصمت، فقد يكون قد أكّد للتو */
    @Override
    protected void onResume() {
        super.onResume();
        checkVerification(false);
    }

    @Override
    protected void onDestroy() {
        // بلا هذا يستمر العدّاد يكتب في شاشة ميتة بعد الخروج
        countdown.removeCallbacks(cooldownTick);
        super.onDestroy();
    }

    /** إرسال رابط التأكيد، مع مهلة إلزامية بعد كل محاولة ناجحة كانت أو مرفوضة */
    private void sendLink() {
        startCooldown();

        authRepository.sendEmailVerification(new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(EmailVerificationActivity.this,
                        R.string.msg_link_sent, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(AuthError error) {
                runOnUiThread(() -> Toast.makeText(EmailVerificationActivity.this,
                        error.messageRes(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startCooldown() {
        cooldownEndsAt = System.currentTimeMillis() + RESEND_COOLDOWN_MS;
        binding.btnResendLink.setEnabled(false);
        countdown.removeCallbacks(cooldownTick);
        cooldownTick.run();
    }

    private final Runnable cooldownTick = new Runnable() {
        @Override
        public void run() {
            long remainingMs = cooldownEndsAt - System.currentTimeMillis();
            if (remainingMs <= 0) {
                binding.btnResendLink.setEnabled(true);
                binding.btnResendLink.setText(R.string.btn_resend_link);
                return;
            }
            binding.btnResendLink.setText(getString(R.string.resend_in_seconds,
                    (int) Math.ceil(remainingMs / 1000.0)));
            countdown.postDelayed(this, 1000L);
        }
    };

    /**
     * إعادة قراءة حالة الحساب من فيرمبا: الفحص التلقائي صامت، أما ضغط الزر فيُظهر
     * رسالة لو لم يتم التأكيد بعد. أول فحص يكتشف عدم التأكيد هو من يرسل الرابط.
     */
    private void checkVerification(boolean manual) {
        if (checking) {
            return;
        }
        checking = true;

        authRepository.refreshEmailVerification(new AuthRepository.ReloadCallback() {
            @Override
            public void onChecked(boolean verified) {
                runOnUiThread(() -> {
                    checking = false;

                    if (verified) {
                        continueToApp();
                        return;
                    }
                    if (!linkSent) {
                        linkSent = true;
                        sendLink();
                    }
                    if (manual) {
                        Toast.makeText(EmailVerificationActivity.this,
                                R.string.msg_email_still_unverified, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(AuthError error) {
                runOnUiThread(() -> {
                    checking = false;
                    if (manual) {
                        Toast.makeText(EmailVerificationActivity.this,
                                error.messageRes(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /** بعد التأكيد: تثبيت الحالة في الوثيقة ثم التوجيه لواجهة الدور أو شاشة المراجعة */
    private void continueToApp() {
        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            signOut();
            return;
        }

        userRepository.markEmailVerified(account.getUid(), true, new UserRepository.ResultCallback() {
            @Override
            public void onSuccess() {
                loadProfile(account);
            }

            @Override
            public void onError() {
                // الحالة مثبتة في فيرمبا، فتعذّر تحديث الوثيقة لا يمنع الدخول
                loadProfile(account);
            }
        });
    }

    private void loadProfile(FirebaseUser account) {
        userRepository.loadUser(account.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onLoaded(User user) {
                user.setEmailVerified(true);
                runOnUiThread(() -> RoleRouter.go(EmailVerificationActivity.this, user));
            }

            @Override
            public void onNotFound() {
                runOnUiThread(EmailVerificationActivity.this::signOut);
            }

            @Override
            public void onError() {
                runOnUiThread(() -> Toast.makeText(EmailVerificationActivity.this,
                        R.string.error_load_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void signOut() {
        authRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
