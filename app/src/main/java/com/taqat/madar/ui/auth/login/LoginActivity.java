package com.taqat.madar.ui.auth.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.ui.RoleRouter;
import com.taqat.madar.core.util.Emails;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthError;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.ActivityLoginBinding;
import com.taqat.madar.ui.auth.role.UserTypeActivity;

/**
 * شاشة تسجيل الدخول: البريد الإلكتروني + كلمة المرور.
 * بعد الدخول تُقرأ بيانات الحساب من Firestore لتوجيه المستخدم حسب حالته.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();

    private String email = "";
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> signIn());

        // رابط إنشاء حساب جديد: يبدأ باختيار نوع الحساب
        binding.tvCreateAccountAction.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, UserTypeActivity.class)));

        binding.tvForgotPassword.setOnClickListener(v -> sendPasswordReset());
    }

    /** التحقق من البريد وكلمة المرور ثم الدخول */
    private void signIn() {
        if (submitting) {
            return;
        }

        email = Emails.normalize(textOf(binding.etLoginEmail.getText()));
        String password = textOf(binding.etLoginPassword.getText());

        if (!Emails.isValid(email)) {
            showError(binding.tilLoginEmail, binding.etLoginEmail, R.string.error_email_invalid);
            return;
        }
        if (password.isEmpty()) {
            showError(binding.tilLoginPassword, binding.etLoginPassword, R.string.error_password_empty);
            return;
        }

        setSubmitting(true);
        authRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onAuthenticated(FirebaseUser account) {
                if (account == null) {
                    runOnUiThread(() -> {
                        setSubmitting(false);
                        Toast.makeText(LoginActivity.this,
                                R.string.error_auth_unknown, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                loadProfile(account);
            }

            @Override
            public void onError(AuthError error) {
                runOnUiThread(() -> showAuthError(error));
            }
        });
    }

    /** قراءة بيانات الحساب المحفوظة في Firestore قبل التوجيه */
    private void loadProfile(FirebaseUser account) {
        userRepository.loadUser(account.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onLoaded(User user) {
                runOnUiThread(() -> finishLogin(user));
            }

            @Override
            public void onNotFound() {
                runOnUiThread(() -> {
                    authRepository.signOut();
                    setSubmitting(false);
                    Toast.makeText(LoginActivity.this,
                            R.string.error_profile_missing, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    setSubmitting(false);
                    Toast.makeText(LoginActivity.this,
                            R.string.error_load_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** خطأ الدخول يخصّ حقلاً بعينه فيُعرض تحته، وغير ذلك يظهر في تنبيه */
    private void showAuthError(AuthError error) {
        setSubmitting(false);

        if (error == AuthError.ACCOUNT_NOT_FOUND) {
            showError(binding.tilLoginEmail, binding.etLoginEmail, error.messageRes());
            return;
        }
        if (error == AuthError.LOGIN_FAILED) {
            showError(binding.tilLoginPassword, binding.etLoginPassword, error.messageRes());
            binding.etLoginPassword.requestFocus();
            return;
        }

        Toast.makeText(this, error.messageRes(), Toast.LENGTH_LONG).show();
    }

    /** إرسال رابط تغيير كلمة المرور للبريد المكتوب */
    private void sendPasswordReset() {
        email = Emails.normalize(textOf(binding.etLoginEmail.getText()));

        if (!Emails.isValid(email)) {
            showError(binding.tilLoginEmail, binding.etLoginEmail, R.string.error_email_invalid);
            return;
        }

        authRepository.sendPasswordReset(email, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this,
                        R.string.msg_reset_sent, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onError(AuthError error) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this,
                        error.messageRes(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * بعد الدخول: تأكيد البريد هو بوابة التطبيق، فالموجّه يتوقف عند شاشة البريد
     * قبل أن يرى الدور أو حالة المراجعة.
     */
    private void finishLogin(User user) {
        if (user.isEmailVerified()) {
            Toast.makeText(this, R.string.msg_welcome_back, Toast.LENGTH_LONG).show();
        }

        RoleRouter.go(this, user);
    }

    private void showError(TextInputLayout layout, EditText field, int messageRes) {
        layout.setError(getString(messageRes));
        field.requestFocus();
    }

    private void setSubmitting(boolean submitting) {
        this.submitting = submitting;
        binding.btnLogin.setEnabled(!submitting);
        binding.btnLogin.setText(submitting
                ? R.string.login_submitting : R.string.btn_login);
    }

    private String textOf(CharSequence value) {
        return TextUtils.isEmpty(value) ? "" : value.toString().trim();
    }
}
