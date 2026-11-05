package com.taqat.madar.core.ui;

import android.app.Activity;
import android.content.Intent;

import com.taqat.madar.data.model.Role;
import com.taqat.madar.data.model.User;
import com.taqat.madar.ui.auth.email.EmailVerificationActivity;
import com.taqat.madar.ui.auth.pending.PendingReviewActivity;
import com.taqat.madar.ui.customer.CustomerHomeActivity;
import com.taqat.madar.ui.merchant.home.MerchantHomeActivity;

/**
 * الوجهة النهائية بعد نجاح الدخول أو إنشاء الحساب، وبوابتها الأولى:
 * من لم يؤكد بريده لا يدخل التطبيق، ومن أكّده يذهب لواجهة دوره أو لشاشة المراجعة.
 *
 * نموذج المتجر ليس محطة في هذا المسار: بيانات المتجر تُكتب مرة واحدة أثناء التسجيل،
 * وتعديلها لاحقاً يتم من بروفايل التاجر بعد الاعتماد.
 */
public final class RoleRouter {

    private RoleRouter() {
    }

    /** يفتح الشاشة المطلوبة ويغلق سلسلة شاشات تسجيل الدخول حتى لا يرجع لها بزر الرجوع */
    public static void go(Activity activity, User user) {
        Class<?> destination;

        if (!user.isEmailVerified()) {
            destination = EmailVerificationActivity.class;
        } else if (user.canEnterApp()) {
            destination = screenFor(user.getRole());
        } else {
            destination = PendingReviewActivity.class;
        }

        Intent intent = new Intent(activity, destination);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    /**
     * TODO: واجهات المندوب والمهني لم تُبنَ بعد، فالحساب المعتمد في هذين الدورين
     *       يفتح واجهة الزبون إلى أن يُسلَّم تصميم كل دور.
     */
    private static Class<?> screenFor(Role role) {
        switch (role) {
            case MERCHANT:
                return MerchantHomeActivity.class;
            case CUSTOMER:
            default:
                return CustomerHomeActivity.class;
        }
    }
}
