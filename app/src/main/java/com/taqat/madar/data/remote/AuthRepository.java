package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.core.util.Emails;

/**
 * كل ما يخص المصادقة في Firebase: إنشاء حساب بالبريد وكلمة المرور، والدخول، وتغيير كلمة المرور.
 *
 * الطريقة المعتمدة في مدار: البريد الإلكتروني + كلمة مرور خاصة بكل مستخدم،
 * أما رقم الجوال فهو معلومة تواصل تُحفظ مع الحساب ولا يُستخدم للدخول.
 */
public class AuthRepository {
    /** اسم فلتر في Logcat لمتابعة شغل المصادقة خطوة بخطوة */
    public static final String LOG_TAG = "MadarAuth";
    /**
     * لغة قوالب البريد في فيرمبا: بدونها يصل بريد التأكيد واسترجاع كلمة المرور
     * بالإنجليزية، لأن فيرمبا يرسل القالب الافتراضي ما لم تُحدَّد اللغة صراحة.
     */
    private static final String EMAIL_LANGUAGE = "ar";
    /** نتيجة إنشاء الحساب أو الدخول */
    public interface AuthCallback {
        /** تم إنشاء الحساب أو الدخول بنجاح */
        void onAuthenticated(FirebaseUser user);

        /** رسالة مفهومة للمستخدم بالعربية */
        void onError(AuthError error);
    }

    /** نتيجة عملية لا تُرجع بيانات، مثل إرسال رابط تغيير كلمة المرور */
    public interface SimpleCallback {
        void onSuccess();

        void onError(AuthError error);
    }

    /** نتيجة إعادة قراءة حالة الحساب من فيرمبا */
    public interface ReloadCallback {
        /** هل أصبح البريد مؤكَّداً الآن؟ */
        void onChecked(boolean emailVerified);

        void onError(AuthError error);
    }

    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

    public AuthRepository() {
        firebaseAuth.setLanguageCode(EMAIL_LANGUAGE);
    }

    /** نص خطأ Firebase الخام (إنجليزي) للتشخيص في البناء التجريبي فقط */
    private String lastTechnicalMessage = "";

    /**
     * إنشاء حساب جديد. البريد يُمرَّر موحّد الأحرف ليكون فريداً:
     * فـ Firebase يفرّق بين الكبيرة والصغيرة، وبدون توحيد يتكرر نفس البريد بصيغ مختلفة.
     */
    public void createAccount(@NonNull String email, @NonNull String password,
                              @NonNull AuthCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(Emails.normalize(email), password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Log.d(LOG_TAG, "createAccount: تم إنشاء الحساب بنجاح");
                        callback.onAuthenticated(task.getResult().getUser());
                    } else {
                        reportFailure("createAccount", task.getException(), callback::onError);
                    }
                });
    }

    /** دخول مستخدم موجود ببريده وكلمة مروره */
    public void signIn(@NonNull String email, @NonNull String password,
                       @NonNull AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(Emails.normalize(email), password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        Log.d(LOG_TAG, "signIn: تم الدخول بنجاح");
                        callback.onAuthenticated(task.getResult().getUser());
                    } else {
                        reportFailure("signIn", task.getException(), callback::onError);
                    }
                });
    }

    /** إرسال بريد فيه رابط إعادة تعيين كلمة المرور */
    public void sendPasswordReset(@NonNull String email, @NonNull SimpleCallback callback) {
        firebaseAuth.sendPasswordResetEmail(Emails.normalize(email))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(LOG_TAG, "sendPasswordReset: الرابط في طريقه للبريد");
                        callback.onSuccess();
                    } else {
                        reportFailure("sendPasswordReset", task.getException(), callback::onError);
                    }
                });
    }

    public FirebaseUser currentUser() {
        return firebaseAuth.getCurrentUser();
    }

    /**
     * إرسال رابط تأكيد البريد للمستخدم الحالي. التأكيد هنا بوابة للدخول: لا يفتح
     * المستخدم التطبيق إلا بعد الضغط على الرابط، فيبقى البريد ملكاً لصاحبه فقط.
     */
    public void sendEmailVerification(@NonNull SimpleCallback callback) {
        FirebaseUser account = firebaseAuth.getCurrentUser();
        if (account == null) {
            callback.onError(AuthError.UNKNOWN);
            return;
        }

        account.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(LOG_TAG, "sendEmailVerification: رابط التأكيد في طريقه للبريد");
                        callback.onSuccess();
                    } else {
                        reportFailure("sendEmailVerification", task.getException(), callback::onError);
                    }
                });
    }

    public boolean isSignedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    /** هل بريد المستخدم الحالي مؤكَّد؟ */
    public boolean isEmailVerified() {
        FirebaseUser account = firebaseAuth.getCurrentUser();
        return account != null && account.isEmailVerified();
    }

    /** بريد المستخدم الحالي كما هو محفوظ في جلسة المصادقة */
    public String currentUserEmail() {
        FirebaseUser account = firebaseAuth.getCurrentUser();
        return account == null || account.getEmail() == null ? "" : account.getEmail();
    }

    /**
     * إعادة قراءة حالة الحساب من فيرمبا بعد ضغط المستخدم على رابط التأكيد،
     * لأن النسخة المحفوظة على الجهاز لا تتحدّث وحدها.
     */
    public void refreshEmailVerification(@NonNull ReloadCallback callback) {
        FirebaseUser account = firebaseAuth.getCurrentUser();
        if (account == null) {
            callback.onError(AuthError.UNKNOWN);
            return;
        }

        account.reload()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        boolean verified = account.isEmailVerified();
                        Log.d(LOG_TAG, "refreshEmailVerification: verified=" + verified);
                        callback.onChecked(verified);
                    } else {
                        reportFailure("refreshEmailVerification", task.getException(), callback::onError);
                    }
                });
    }

    public void signOut() {
        firebaseAuth.signOut();
    }

    /** تفاصيل آخر خطأ، تُعرض في شاشة التطوير لمعرفة سبب الفشل الحقيقي */
    public String lastTechnicalMessage() {
        return lastTechnicalMessage;
    }

    /** تسجيل السبب الخام ثم تحويله لنوع خطأ معروف قبل عرضه */
    private void reportFailure(String action, Exception exception, ErrorSink sink) {
        if (exception == null) {
            lastTechnicalMessage = "unknown";
        } else {
            lastTechnicalMessage = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
        }

        Log.e(LOG_TAG, action + " failed: " + lastTechnicalMessage, exception);
        sink.accept(describe(exception));
    }

    /** واجهة داخلية تختصر تمرير مستمع الخطأ */
    private interface ErrorSink {
        void accept(AuthError error);
    }

    /**
     * ترجمة الخطأ باستخدام أكواد Firebase الرسمية (دقيقة)، مع رجوع لمطابقة النص
     * لو وصل خطأ من نوع آخر لا يحمل كوداً.
     */
    private AuthError describe(Throwable error) {
        // فير بيزيز بيرجع حظر الجهاز من نوع مستقل ليس FirebaseAuthException،
        // فلو تُرِك يسقط في UNKNOWN يبقى المستخدم بلا عنوان: لا هو خطأ شبكة ولا خطأ إدخال
        if (error instanceof FirebaseTooManyRequestsException) {
            return AuthError.TOO_MANY_ATTEMPTS;
        }

        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();

            switch (code) {
                case "ERROR_EMAIL_ALREADY_IN_USE":
                case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL":
                    return AuthError.EMAIL_IN_USE;
                case "ERROR_INVALID_EMAIL":
                    return AuthError.EMAIL_INVALID;
                case "ERROR_WEAK_PASSWORD":
                    return AuthError.PASSWORD_WEAK;
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_INVALID_LOGIN_CREDENTIALS":
                    return AuthError.LOGIN_FAILED;
                case "ERROR_USER_NOT_FOUND":
                case "ERROR_USER_DISABLED":
                    return AuthError.ACCOUNT_NOT_FOUND;
                case "ERROR_TOO_MANY_REQUESTS":
                    return AuthError.TOO_MANY_ATTEMPTS;
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return AuthError.PROVIDER_DISABLED;
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return AuthError.NETWORK;
                default:
                    break;
            }
        }

        String message = error == null || error.getMessage() == null
                ? "" : error.getMessage().toLowerCase();

        if (message.contains("network") || message.contains("timeout")
                || message.contains("unavailable") || message.contains("backend")
                || message.contains("io exception")) {
            return AuthError.NETWORK;
        }
        // صيغة الحظر التي ترجع بها خدمة المصادقة حين تتكرر الطلبات من نفس الجهاز
        if (message.contains("blocked") || message.contains("unusual activity")
                || message.contains("too many")) {
            return AuthError.TOO_MANY_ATTEMPTS;
        }
        if (message.contains("email") && message.contains("use")) {
            return AuthError.EMAIL_IN_USE;
        }
        if (message.contains("password")) {
            return AuthError.LOGIN_FAILED;
        }
        return AuthError.UNKNOWN;
    }
}
