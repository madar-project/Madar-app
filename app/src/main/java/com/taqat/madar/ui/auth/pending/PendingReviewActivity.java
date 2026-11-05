package com.taqat.madar.ui.auth.pending;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.ui.RoleRouter;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.ActivityPendingReviewBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.merchant.notifications.MerchantNotificationFeed;

/**
 * شاشة من ينتظر موافقة إدارة مدار (تاجر / مهني / مندوب):
 * تعرض ملخص الطلب وتغلق الجلسة عند العودة.
 *
 * الشاشة مستمعة لوثيقة المستخدم لا تقرأ مرة ثم تسكت، لأن قرار الإدارة يصلها لحظة صدوره:
 * القبول يوجهها لواجهة الدور، والرفض عند مدار يعني حذف الحساب فتتحول هي إلى إشعار الرفض.
 */
public class PendingReviewActivity extends AppCompatActivity {

    private ActivityPendingReviewBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();

    private ListenerRegistration statusWatch;
    /** لا انتقال بعد الآن: إما شاشة الرفض وإما رحنا لواجهة الدور */
    private boolean settled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPendingReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        binding.btnPendingBack.setOnClickListener(v -> signOut());
    }

    @Override
    protected void onStart() {
        super.onStart();
        watchStatus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (statusWatch != null) {
            statusWatch.remove();
            statusWatch = null;
        }
    }

    /**
     * أول لقطة تحمل الحالة الحالية، وكل لقطة بعدها تحمل قرار الإدارة الجديد.
     */
    private void watchStatus() {
        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            signOut();
            return;
        }

        statusWatch = userRepository.watchUser(account.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onLoaded(@NonNull User user) {
                runOnUiThread(() -> {
                    if (settled || isFinishing()) {
                        return;
                    }
                    if (user.canEnterApp()) {
                        settled = true;
                        // خبر الاعتماد يُسجّل قبل التوجيه: قد لا يعود التاجر لهذه الشاشة
                        // إلا بعد يوم، فيلقاه في إشعاراته لا ضائعاً بين شاشتين
                        MerchantNotificationFeed.recordStoreApproved(
                                PendingReviewActivity.this, account.getUid());
                        RoleRouter.go(PendingReviewActivity.this, user);
                    } else {
                        bind(user, account.getEmail());
                    }
                });
            }

            @Override
            public void onNotFound() {
                // الوثيقة كانت موجودة ثم ذهبت: الرفض تنفيذُه حذف، فلا رسالة أوضح من هذه
                runOnUiThread(PendingReviewActivity.this::showRejected);
            }

            @Override
            public void onAccountGone() {
                // حساب المصادقة حُذف فقراءة وثائقه صارت مرفوضة: النتيجة شاشة الرفض نفسها
                runOnUiThread(PendingReviewActivity.this::showRejected);
            }

            @Override
            public void onError() {
                // الانقطاع ليس قراراً: تبقى شاشة الانتظار على حالها ونخبره فقط
                runOnUiThread(() -> Toast.makeText(PendingReviewActivity.this,
                        R.string.error_load_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void bind(User user, String sessionEmail) {
        binding.tvPendingName.setText(user.getFullName());
        binding.tvPendingRole.setText(user.getRole().displayNameRes());
        binding.tvPendingEmail.setText(TextUtils.isEmpty(sessionEmail)
                ? user.getEmail() : sessionEmail);
    }

    /** عرض نتيجة الرفض مكان شاشة الانتظار: لا ملخص لبيانات حُذفت، ولا انتظار بعد قرار */
    private void showRejected() {
        if (settled || isFinishing()) {
            return;
        }
        settled = true;

        binding.ivPendingIcon.setImageResource(R.drawable.ic_close);
        binding.ivPendingIcon.setImageTintList(
                ContextCompat.getColorStateList(this, R.color.danger_red));
        binding.tvPendingTitle.setText(R.string.rejected_title);
        binding.tvPendingMessage.setText(R.string.rejected_message);
        binding.cardPendingSummary.setVisibility(View.GONE);
        binding.tvPendingNotice.setVisibility(View.GONE);
    }

    private void signOut() {
        settled = true;
        authRepository.signOut();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
