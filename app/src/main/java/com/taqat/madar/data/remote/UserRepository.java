package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.data.model.User;

import java.util.Map;

/**
 * القراءة والكتابة من مجموعة users في Firestore.
 * كل شغل قاعدة البيانات يمر من هنا حتى لا تتكرر أوامر Firebase داخل الشاشات.
 */
public class UserRepository {

    /** اسم فلتر في Logcat: فشل الحفظ أو القراءة بلا سبب مطبوع يبقى لغزاً للشاشة */
    public static final String LOG_TAG = "MadarUsers";

    public interface ResultCallback {
        void onSuccess();

        void onError();
    }

    public interface UserCallback {
        /** الوثيقة موجودة وتمت قراءتها */
        void onLoaded(User user);

        /** لا توجد وثيقة لهذا المعرّف: حساب أُنشئ في المصادقة ولم تُحفظ بياناته، أو حُذف */
        void onNotFound();

        void onError();

        /**
         * القراءة رُفضت بصلاحية مرفوضة: حساب المصادقة نفسه حُذف من سيرفر الإدارة،
         * فالرفض عند مدار معناه زوال الوثائق كلها. حالة مستقلة عن فقدان الشبكة،
         * لأن الانقطاع لا يعني قراراً، والحذف يعني قراراً نهائياً.
         */
        default void onAccountGone() {
            onError();
        }
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /**
     * حفظ بيانات مستخدم جديد بعد نجاح إنشاء الحساب.
     * ملاحظة: تُستخدم set() مرة واحدة عند الإنشاء، ووقت التحديث سنستخدم update()
     * حتى لا يتغير تاريخ الإنشاء.
     */
    public void saveUser(@NonNull User user, @NonNull ResultCallback callback) {
        Map<String, Object> data = user.toMap();
        data.put(User.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        firestore.collection(User.COLLECTION)
                .document(user.getUid())
                .set(data)
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "saveUser failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }

    /**
     * تحديث حالة تأكيد البريد في الوثيقة بعد ضغط المستخدم على الرابط.
     * نستخدم update() بدل set() حتى لا تُعاد كتابة بقية الحقول وتاريخ الإنشاء.
     */
    public void markEmailVerified(@NonNull String uid, boolean verified, @NonNull ResultCallback callback) {
        firestore.collection(User.COLLECTION)
                .document(uid)
                .update(User.FIELD_EMAIL_VERIFIED, verified)
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "markEmailVerified failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }

    /**
     * تحديث رقم الجوال وحده على وثيقة الحساب.
     *
     * update() لا set(): الرقم وحده ما يتغيّر عند التاجر، وكتابة الوثيقة كاملة تعيد
     * تاريخ الإنشاء وتمسس حالة الحساب، والقواعد ترفض أي كتابة تغيّرها من غير الإدارة.
     */
    public void updatePhone(@NonNull String uid, @NonNull String phone,
                            @NonNull ResultCallback callback) {
        firestore.collection(User.COLLECTION)
                .document(uid)
                .update(User.FIELD_PHONE, phone)
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "updatePhone failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }

    /** قراءة بيانات مستخدم معرّفه معروفة (للتوجيه إلى شاشة دوره أو شاشة المراجعة) */
    public void loadUser(@NonNull String uid, @NonNull UserCallback callback) {
        firestore.collection(User.COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onLoaded(User.fromMap(snapshot.getId(), snapshot.getData()));
                    } else {
                        callback.onNotFound();
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "loadUser failed: " + error.getMessage(), error);
                    if (isPermissionDenied(error)) {
                        callback.onAccountGone();
                    } else {
                        callback.onError();
                    }
                });
    }

    /**
     * متابعة وثيقة المستخدم بدل قراءتها مرة واحدة، حتى تنتقل شاشة المراجعة إلى
     * واجهة الدور لحظة موافقة الإدارة من غير ما يقفل المستخدم التطبيق ويعيد فتحه.
     *
     * الاستماع يبقى حيّاً طالما الشاشة ظاهرة، فعلى الشاشة إزالته في onStop بالكائن
     * المُرجَع، لئلا يعمل وهو مخفي ويوجّه المستخدم في الخفاء.
     */
    @NonNull
    public ListenerRegistration watchUser(@NonNull String uid, @NonNull UserCallback callback) {
        return firestore.collection(User.COLLECTION)
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(LOG_TAG, "watchUser failed: " + error.getMessage(), error);
                        if (isPermissionDenied(error)) {
                            callback.onAccountGone();
                        } else {
                            callback.onError();
                        }
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onNotFound();
                        return;
                    }
                    callback.onLoaded(User.fromMap(snapshot.getId(), snapshot.getData()));
                });
    }

    /**
     * هل الخطأ رفض صلاحية؟ حذف حساب المصادقة يُبطل توكنه، فتتحول قراءات فيرستور
     * إلى رفض صلاحيات بدل أن تقول «لا توجد وثيقة». وبلا هذا التمييز تُحسب الشبكة
     * المنقطعة قرار طرد، ويحسب قرار الطرد انقطاع شبكة.
     */
    private static boolean isPermissionDenied(Throwable error) {
        return error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }
}
