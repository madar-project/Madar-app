package com.taqat.madar.ui.auth.register;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.BuildConfig;
import com.taqat.madar.R;
import com.taqat.madar.core.constants.Extras;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.ui.RoleRouter;
import com.taqat.madar.core.util.Emails;
import com.taqat.madar.core.util.Passwords;
import com.taqat.madar.core.util.PhoneNumbers;
import com.taqat.madar.data.model.Role;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthError;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.ActivityRegisterBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;

/**
 * شاشة إنشاء حساب: الاسم والبريد الإلكتروني ورقم الجوال وكلمة المرور.
 * البريد هو هوية المستخدم للدخول، ورقم الجوال معلومة تواصل تُحفظ مع الحساب.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();

    /** الدور الذي اختاره المستخدم في شاشة "اختر نوع حسابك" */
    private Role userRole = Role.CUSTOMER;

    private String fullName = "";
    private String email = "";
    private String phoneE164 = "";
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        userRole = Role.fromValue(getIntent().getStringExtra(Extras.USER_ROLE));
        binding.tvSubtitle.setText(subtitleFor(userRole));

        binding.btnRegister.setOnClickListener(v -> submitRegistration());

        binding.tvLoginAction.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    /** التحقق من الحقول الخمسة، ثم إنشاء الحساب وحفظ بياناته */
    private void submitRegistration() {
        if (submitting) {
            return;
        }

        fullName = textOf(binding.etName.getText());
        email = Emails.normalize(textOf(binding.etEmail.getText()));
        String phoneInput = textOf(binding.etPhone.getText());
        String password = textOf(binding.etPassword.getText());
        String confirmation = textOf(binding.etConfirmPassword.getText());

        if (fullName.length() < 3) {
            showError(binding.tilName, binding.etName, R.string.error_name_short);
            return;
        }
        if (!Emails.isValid(email)) {
            showError(binding.tilEmail, binding.etEmail, R.string.error_email_invalid);
            return;
        }
        if (!PhoneNumbers.isValidPalestineMobile(phoneInput)) {
            showError(binding.tilPhone, binding.etPhone, R.string.error_phone_invalid);
            return;
        }
        if (!Passwords.isStrongEnough(password)) {
            showError(binding.tilPassword, binding.etPassword, R.string.error_password_weak);
            return;
        }
        if (!Passwords.doMatch(password, confirmation)) {
            showError(binding.tilConfirmPassword, binding.etConfirmPassword,
                    R.string.error_password_mismatch);
            return;
        }

        phoneE164 = PhoneNumbers.toE164(phoneInput);
        setSubmitting(true);

        authRepository.createAccount(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onAuthenticated(FirebaseUser account) {
                if (account == null) {
                    runOnUiThread(() -> {
                        setSubmitting(false);
                        Toast.makeText(RegisterActivity.this,
                                R.string.error_auth_unknown, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                saveProfile(account);
            }

            @Override
            public void onError(AuthError error) {
                runOnUiThread(() -> handleAuthError(error));
            }
        });
    }

    /** بعض أخطاء Firebase تخص حقلاً بعينه، فنعرضه تحته بدل تنبيه عام */
    private void handleAuthError(AuthError error) {
        setSubmitting(false);

        if (error == AuthError.EMAIL_IN_USE) {
            showError(binding.tilEmail, binding.etEmail, error.messageRes());
            return;
        }
        if (error == AuthError.PASSWORD_WEAK) {
            showError(binding.tilPassword, binding.etPassword, error.messageRes());
            return;
        }

        String message = getString(error.messageRes());
        String detail = authRepository.lastTechnicalMessage();
        if (BuildConfig.DEBUG && !TextUtils.isEmpty(detail)) {
            message = message + " — " + shorten(detail);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** حفظ بيانات الملف الشخصي في Firestore بعد نجاح إنشاء الحساب */
    private void saveProfile(FirebaseUser account) {
        User profile = new User(account.getUid(), fullName, email, phoneE164, userRole);
        profile.setEmailVerified(account.isEmailVerified());

        userRepository.saveUser(profile, new UserRepository.ResultCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> finishRegistration(profile));
            }

            @Override
            public void onError() {
                runOnUiThread(() -> {
                    // الحساب نشأ في المصادقة لكن بياناته لم تُحفظ، فنغلق الجلسة لتعاد المحاولة
                    authRepository.signOut();
                    setSubmitting(false);
                    Toast.makeText(RegisterActivity.this,
                            R.string.error_save_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** بعد الحفظ: تنبيه قصير ثم توجيه المستخدم لواجهة دوره أو شاشة المراجعة */
    private void finishRegistration(User profile) {
        Toast.makeText(this, profile.canEnterApp()
                ? R.string.msg_account_created_customer : R.string.msg_account_pending,
                Toast.LENGTH_LONG).show();

        RoleRouter.go(this, profile);
    }

    private void showError(TextInputLayout layout, EditText field, int messageRes) {
        layout.setError(getString(messageRes));
        field.requestFocus();
    }

    private void setSubmitting(boolean submitting) {
        this.submitting = submitting;
        binding.btnRegister.setEnabled(!submitting);
        binding.btnRegister.setText(submitting
                ? R.string.register_submitting : R.string.btn_register);
    }

    private int subtitleFor(Role role) {
        switch (role) {
            case MERCHANT:
                return R.string.register_subtitle_merchant;
            case PROFESSIONAL:
                return R.string.register_subtitle_professional;
            case DELIVERY:
                return R.string.register_subtitle_delivery;
            default:
                return R.string.register_subtitle_customer;
        }
    }

    /** أول سطر من رسالة Firebase الخام، بطول يبقى مقروءاً في التنبيه */
    private String shorten(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 110 ? single : single.substring(0, 110) + "…";
    }

    private String textOf(CharSequence value) {
        return TextUtils.isEmpty(value) ? "" : value.toString().trim();
    }
}
