package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.taqat.madar.data.model.AccountStatus;
import com.taqat.madar.data.model.MerchantProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * القراءة والكتابة من مجموعة merchants.
 *
 * كون مفتاح الوثيقة هو معرّف حساب صاحب المتجر يمنع تلقائياً أن يفتح شخص واحد
 * أكثر من متجر، أو أن يرسل نفس الطلب مرات عدة ليزحم المراجعة.
 */
public class MerchantProfileRepository {

    /** اسم فلتر في Logcat لمتابعة قراءة وكتابة ملفات المتاجر */
    public static final String LOG_TAG = "MadarMerchants";

    public interface WriteCallback {
        void onSuccess();

        void onError();
    }

    public interface ProfileCallback {
        /** الوثيقة موجودة وتمت قراءتها */
        void onLoaded(@NonNull MerchantProfile profile);

        /** لا ملف متجر لهذا الحساب بعد */
        void onNotFound();

        void onError();
    }

    /** نتيجة متابعة ملف المتجر وهو حيّ */
    public interface LiveCallback {
        /** ملف موجود، وتُستدعى عند كل تغيير */
        void onProfile(@NonNull MerchantProfile profile);

        /**
         * الوثيقة اختفت أو سقطت الصلاحية عنها: الرفض عند إدارة مدار هو حذف،
         * فغياب الملف بعد أن كان موجوداً معناه أن الحساب حُذف من طرفهم.
         */
        void onRemoved();

        /** تعذّرت القراءة للشبكة: تبقى الشاشة على حالها، ما بنفسّر الانقطاع قراراً */
        void onUnavailable();
    }

    /** هل للحساب ملف متجر؟ */
    public interface ExistsCallback {
        void onResult(boolean found);
    }

    /** لائحة متاجر معتمدة يقرأها الزبون في شاشة التصفّح */
    public interface ListCallback {
        void onStores(@NonNull List<MerchantProfile> stores);

        void onError();
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /**
     * حفظ ملف المتجر بعد التقاط صورته.
     *
     * القاعدة: أول إرسال يدخل المراجعة معلّقاً، ولا الرجوع للتعديل ولا التعديل اليدوي
     * للحالة متاح من التطبيق؛ أما تعديل بيانات متجر معتمد فيحفظ التغييرات بلا إعادتها
     * للمراجعة، لأن الذي تغيّر عنوان أو صورة وليس هوية صاحب النشاط.
     */
    public void save(@NonNull MerchantProfile profile, @NonNull WriteCallback callback) {
        Map<String, Object> data = profile.toMap();
        DocumentReference document = firestore.collection(MerchantProfile.COLLECTION)
                .document(profile.getUid());

        if (profile.isApproved()) {
            data.remove(MerchantProfile.FIELD_STATUS);
            document.update(data)
                    .addOnSuccessListener(ignored -> callback.onSuccess())
                    .addOnFailureListener(error -> {
                        Log.e(LOG_TAG, "save approved failed: " + error.getMessage(), error);
                        callback.onError();
                    });
            return;
        }

        data.put(MerchantProfile.FIELD_STATUS, AccountStatus.PENDING.value());
        data.put(MerchantProfile.FIELD_SUBMITTED_AT, FieldValue.serverTimestamp());
        document.set(data, SetOptions.merge())
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "save pending failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }

    /**
     * تحريك سويتش «مفتوح / مغلق» وحده، من غير لمس بقية الحقول.
     *
     * التحديث جزئي عن قصد: قواعد الأمان تسمح للتاجر أن يغيّر حالته ما دام لم يمسس
     * حقل الحالة، فكتابة الوثيقة كاملة كانت ستفتح ثغرة يعيد فيها اعتماد نفسه.
     */
    public void setAcceptingOrders(@NonNull String uid, boolean accepting,
                                  @NonNull WriteCallback callback) {
        firestore.collection(MerchantProfile.COLLECTION)
                .document(uid)
                .update(MerchantProfile.FIELD_ACCEPTING_ORDERS, accepting)
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "setAcceptingOrders failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }

    /** قراءة ملف المتجر لمعرّف معروف، لعرضه في واجهة التاجر أو لتعبئة نموذج التعديل */
    public void load(@NonNull String uid, @NonNull ProfileCallback callback) {
        firestore.collection(MerchantProfile.COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onLoaded(MerchantProfile.fromMap(snapshot.getId(), snapshot.getData()));
                    } else {
                        callback.onNotFound();
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "load failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }

    /** هل للحساب ملف متجر؟ تُستخدم في التوجيه بين نموذج التسجيل وواجهة التاجر */
    public void exists(@NonNull String uid, @NonNull ExistsCallback callback) {
        firestore.collection(MerchantProfile.COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> callback.onResult(snapshot.exists()))
                .addOnFailureListener(error -> {
                    // الخطأ يُعامَل كأنه غياب للوثيقة، فمن دون طباعة لا يُفرَّق بين الاثنين
                    Log.e(LOG_TAG, "exists failed: " + error.getMessage(), error);
                    callback.onResult(false);
                });
    }

    /**
     * متابعة ملف المتجر لحظة بلحظة بدل قراءته مرة واحدة.
     *
     * وجوده ضروري لأن الرفض صار حذفاً: التاجر الجالس على واجهته يفقد متجره وهو عليها،
     * فبدل هذا الاستماع يبقى شاشة عرض بيانات حذفت من قبل.
     *
     * على الشاشة إزالة الاستماع في onStop بالكائن المُرجَع، لئلا يستمر وهو مخفي.
     */
    @NonNull
    public ListenerRegistration watch(@NonNull String uid, @NonNull LiveCallback callback) {
        return firestore.collection(MerchantProfile.COLLECTION)
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(LOG_TAG, "watch stopped: " + error.getMessage(), error);
                        // الحساب المحذوف يفقد صلاحيته، فقراءته تُرفض لا بأنها لا توجد
                        if (error.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            callback.onRemoved();
                        } else {
                            callback.onUnavailable();
                        }
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        callback.onRemoved();
                        return;
                    }
                    callback.onProfile(MerchantProfile.fromMap(snapshot.getId(), snapshot.getData()));
                });
    }

    /**
     * المتاجر المعتمدة في تصنيفات بعينها، وهي لائحة الزبون التي تفتح من بطاقة القسم.
     *
     * السؤال على التصنيف وحده، وحالة الاعتماد تُفرَز في الكود: شرط واحد يعني فهرساً
     * واحداً في الكونسول، والاعتماد حقل تغيّره لوحة التحكم فلا يُبنى عليه استعلام.
     * والقائمة الفارغة قسم لم يُبنَ بعد (التوصيل الخاص والخدمات المهنية)، فلا سبب
     * لسؤال السيرفر عن شيء معروفة إجابته قبل السؤال.
     */
    public void loadByCategories(@NonNull List<String> categories, @NonNull ListCallback callback) {
        if (categories.isEmpty()) {
            callback.onStores(new ArrayList<>());
            return;
        }

        firestore.collection(MerchantProfile.COLLECTION)
                .whereIn(MerchantProfile.FIELD_CATEGORY, categories)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.e(LOG_TAG, "loadByCategories failed: "
                                + (task.getException() == null ? "unknown" : task.getException().getMessage()));
                        callback.onError();
                        return;
                    }

                    List<MerchantProfile> stores = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        MerchantProfile profile =
                                MerchantProfile.fromMap(document.getId(), document.getData());
                        // غير المعتمد ما بعد صار واجهة متجر، فهو للتاجر ولوحة التحكم لا للزبون
                        if (profile.isApproved()) {
                            stores.add(profile);
                        }
                    }
                    callback.onStores(stores);
                });
    }

    /**
     * حذف ملف متجر. الحذف ممنوع في قواعد الأمان على أي مستخدم، فتبقى هذه الدالة
     * للاختبار الداخلي فقط: الرفض الحقيقي تنفّذه لوحة التحكم من سيرفرها بصلاحية
     * تتجاوز قواعد الوصول، وتحذف معه حساب المستخدم في المصادقة وصور المتجر.
     */
    public void delete(@NonNull String uid, @NonNull WriteCallback callback) {
        firestore.collection(MerchantProfile.COLLECTION)
                .document(uid)
                .delete()
                .addOnSuccessListener(ignored -> callback.onSuccess())
                .addOnFailureListener(error -> {
                    Log.e(LOG_TAG, "delete failed: " + error.getMessage(), error);
                    callback.onError();
                });
    }
}
