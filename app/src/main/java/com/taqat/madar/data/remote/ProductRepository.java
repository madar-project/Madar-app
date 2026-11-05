package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.taqat.madar.data.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * القراءة والكتابة من مجموعة products.
 *
 * صاحب المتجر هو من يكتب منتجاته، والقاعدة في firestore.rules ترفض أي منتج ينسب
 * نفسه لمتجر آخر: الحقل storeId في الكتابة يجب أن يكون معرّف الكاتب نفسه.
 */
public class ProductRepository {

    /** اسم فلتر في Logcat لمتابعة مشاكل المنتجات */
    public static final String LOG_TAG = "MadarProducts";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface WriteCallback {
        void onSuccess();

        void onError();
    }

    /** قائمة منتجات متجر واحد، مرتّبة الأحدث أولاً كما يريدها التاجر في منيوه */
    public interface ListCallback {
        void onProducts(@NonNull List<Product> products);

        void onError();
    }

    /**
     * حفظ منتج: وثيقة جديدة بمعرّف تُنشئه Firestore حين يكون معرّف المنتج فارغاً،
     * أو تحديث وثيقته المعروفة حين جاء بها.
     *
     * تاريخ الإنشاء يُضاف عند الإنشاء مرة واحدة ويُستثنى من التحديث: تعديل سعر طبق
     * لا يغيّر متى نُشر، ولو كتبناه من جديد لضاع ترتيب اللائحة الذي يُبنى عليه.
     */
    public void save(@NonNull Product product, @NonNull WriteCallback callback) {
        if (product.getStoreId().isEmpty()) {
            Log.e(LOG_TAG, "save refused: product has no storeId");
            callback.onError();
            return;
        }

        Map<String, Object> data = product.toMap();

        if (product.getId().isEmpty()) {
            data.put(Product.FIELD_CREATED_AT, FieldValue.serverTimestamp());
            firestore.collection(Product.COLLECTION).document()
                    .set(data)
                    .addOnCompleteListener(task -> report(task.isSuccessful(),
                            task.getException(), callback, "save"));
            return;
        }

        firestore.collection(Product.COLLECTION).document(product.getId())
                .update(data)
                .addOnCompleteListener(task -> report(task.isSuccessful(),
                        task.getException(), callback, "update"));
    }

    /**
     * تحريك سويتش «توفر الصنف» وحده من غير لمس بقية الحقول: التحديث جزئي عن قصد،
     * لأن كتابة الوثيقة كاملة من الشاشة تعني إعادة سعر ووصف قد يكونان تغيّرا في مكان آخر.
     */
    public void setAvailable(@NonNull String productId, boolean available,
                             @NonNull WriteCallback callback) {
        firestore.collection(Product.COLLECTION).document(productId)
                .update(Product.FIELD_AVAILABLE, available)
                .addOnCompleteListener(task -> report(task.isSuccessful(),
                        task.getException(), callback, "setAvailable"));
    }

    /** منتجات متجر واحد: مسار الزبون ولائحة التاجر كلاهما يبدأ من هنا */
    public void loadForStore(@NonNull String storeId, @NonNull ListCallback callback) {
        firestore.collection(Product.COLLECTION)
                .whereEqualTo(Product.FIELD_STORE_ID, storeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.e(LOG_TAG, "loadForStore failed: " + messageOf(task.getException()));
                        callback.onError();
                        return;
                    }
                    callback.onProducts(productsOf(task.getResult()));
                });
    }

    /**
     * منتجات عدة متاجر في سؤال واحد: whereIn ما يقبل أكثر من عشرة معرّفات، فتُقسَم
     * اللائحة وتُجمَع نتائجها. شاشة تصفّح الزبون تحتاج أسرع وقت تحضير لكل متجر ليكتب
     * على بطاقته «15-25 دقيقة»، وسؤال متجر متجر كان سيحوّل اللائحة إلى رحلة لكل بطاقة.
     *
     * الرحلة الساقطة تُترَك بلا منتجات لا تُبطل الباقي: وقت التحضير كلمة على البطاقة،
     * وأحسن من لائحة كاملة تختفي لأن متجر واحد لم يصل.
     */
    public void loadForStores(@NonNull List<String> storeIds, @NonNull ListCallback callback) {
        if (storeIds.isEmpty()) {
            callback.onProducts(new ArrayList<>());
            return;
        }

        List<List<String>> chunks = chunksOf(storeIds);
        List<Product> merged = new ArrayList<>();
        int[] pending = {chunks.size()};

        for (List<String> chunk : chunks) {
            firestore.collection(Product.COLLECTION)
                    .whereIn(Product.FIELD_STORE_ID, chunk)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            merged.addAll(productsOf(task.getResult()));
                        } else {
                            Log.e(LOG_TAG, "loadForStores failed: " + messageOf(task.getException()));
                        }
                        if (--pending[0] == 0) {
                            callback.onProducts(merged);
                        }
                    });
        }
    }

    /** تقسيم على عشرة عشرة، وهو أقصى ما يقبله whereIn في Firestore */
    @NonNull
    private static List<List<String>> chunksOf(@NonNull List<String> values) {
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < values.size(); from += 10) {
            chunks.add(values.subList(from, Math.min(from + 10, values.size())));
        }
        return chunks;
    }

    /**
     * حذف منتج: يملكه صاحبه وحده، والقاعدة تسمح له بذلك على وثيقته. حذف المتجر كله
     * عند الرفض لا يتمّ من هنا بل من سيرفر الإدارة، لأنه يحتاج بحثاً ثم مسحاً.
     */
    public void delete(@NonNull String productId, @NonNull WriteCallback callback) {
        firestore.collection(Product.COLLECTION).document(productId)
                .delete()
                .addOnCompleteListener(task -> report(task.isSuccessful(),
                        task.getException(), callback, "delete"));
    }

    /**
     * متابعة منتجات المتجر لحظة بلحظة بدل قراءتها مرة: التاجر اللي قاعد على لائحة
     * منيوه لازم يشوف المنتج الجديد فيها فور ما ينحفظ، والسويتش وهو ينعكس من سيرفره.
     *
     * على الشاشة إزالة الاستماع في onStop بالكائن المُرجَع، لئلا يستمر وهو مخفي.
     */
    @NonNull
    public ListenerRegistration watchForStore(@NonNull String storeId, @NonNull ListCallback callback) {
        return firestore.collection(Product.COLLECTION)
                .whereEqualTo(Product.FIELD_STORE_ID, storeId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(LOG_TAG, "watchForStore stopped: " + messageOf(error), error);
                        callback.onError();
                        return;
                    }
                    if (snapshot == null) {
                        callback.onProducts(new ArrayList<>());
                        return;
                    }
                    callback.onProducts(productsOf(snapshot));
                });
    }

    /** تسجيل السبب الخام ثم إبلاغ المستمع، فلا تُترك الشاشة بلا عنوان على الفشل */
    private static void report(boolean success, Exception error,
                               @NonNull WriteCallback callback, String action) {
        if (success) {
            Log.d(LOG_TAG, action + ": done");
            callback.onSuccess();
            return;
        }
        Log.e(LOG_TAG, action + " failed: " + messageOf(error));
        callback.onError();
    }

    private static String messageOf(Exception error) {
        return error == null || error.getMessage() == null ? "unknown" : error.getMessage();
    }

    @NonNull
    private static List<Product> productsOf(@NonNull QuerySnapshot snapshot) {
        List<DocumentSnapshot> documents = new ArrayList<>(snapshot.getDocuments());
        // الأحدث أولاً يُرتّب في الكود لا في الاستعلام: شرط تساوي مع ترتيب
        // على حقل آخر يطلب فهرساً مركّباً في الكونسول، ولائحة متجر واحد بعشرات
        // البطاقات ما تستاهل تعريفاً إضافياً يتعطل عليه التطبيق عند أول نسيان
        Collections.sort(documents, (left, right) ->
                Long.compare(createdAt(right), createdAt(left)));

        List<Product> products = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            products.add(Product.fromMap(document.getId(), document.getData()));
        }
        return products;
    }

    /** تاريخ لم يصل بعد من السيرفر يُصوَّر أقدم شيء، فيقعد تحت لا في وسط اللائحة */
    private static long createdAt(@NonNull DocumentSnapshot document) {
        Timestamp created = document.getTimestamp(Product.FIELD_CREATED_AT);
        return created == null ? 0 : created.getSeconds();
    }
}
