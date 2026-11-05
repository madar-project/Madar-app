package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.taqat.madar.data.model.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * القراءة والكتابة من مجموعة orders.
 *
 * هذا الملف للتاجر وحده: يقرأ طلبات متجره ويغيّر حالتها. الإنشاء مش هنا — يكتبه
 * مسار طلب الزبون حين يُبنى، لأن الوثيقة لازم تنسب نفسها لصاحبها لا لكاتب لاحق.
 */
public class OrderRepository {

    /** اسم فلتر في Logcat لمتابعة مشاكل الطلبات */
    public static final String LOG_TAG = "MadarOrders";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface WriteCallback {
        void onSuccess();

        void onError();
    }

    /** قائمة طلبات متجر واحد، الأحدث أولاً كما في التصميم */
    public interface ListCallback {
        void onOrders(@NonNull List<Order> orders);

        void onError();
    }

    /**
     * متابعة طلبات المتجر لحظة بلحظة: الطلب الجديد لازم يوصل التاجر وهو قاعد،
     * لا لمّا يقفل الصفحة وتفتحها. الاستماع يُزال في onStop بالكائن المُرجَع.
     */
    @NonNull
    public ListenerRegistration watchForStore(@NonNull String storeId, @NonNull ListCallback callback) {
        return firestore.collection(Order.COLLECTION)
                .whereEqualTo(Order.FIELD_STORE_ID, storeId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(LOG_TAG, "watchForStore stopped: " + messageOf(error), error);
                        callback.onError();
                        return;
                    }
                    if (snapshot == null) {
                        callback.onOrders(new ArrayList<>());
                        return;
                    }
                    callback.onOrders(ordersOf(snapshot));
                });
    }

    /**
     * متابعة طلبات الزبون: بطاقته في رئيسيته تظهر ما دام فيه طلب لم يُغلَق، وتختفي
     * حين يكتمل أو يرفض. الشرط تساوي على معرّفه وحده، فلا فهرس مركّب مطلوب في الكونسول،
     * وقواعد الأمان تسمح له بقراءة طلباته هو لا طلبات غيره.
     */
    @NonNull
    public ListenerRegistration watchForCustomer(@NonNull String customerUid, @NonNull ListCallback callback) {
        return firestore.collection(Order.COLLECTION)
                .whereEqualTo(Order.FIELD_CUSTOMER_UID, customerUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(LOG_TAG, "watchForCustomer stopped: " + messageOf(error), error);
                        callback.onError();
                        return;
                    }
                    if (snapshot == null) {
                        callback.onOrders(new ArrayList<>());
                        return;
                    }
                    callback.onOrders(ordersOf(snapshot));
                });
    }

    /**
     * نقل حالة الطلب: القبول يحطّه قيد التحضير، والرفض يحطّه مرفوض. التحديث جزئي
     * على حقل الحالة وحده — لأن كتابة الوثيقة كاملة من شاشة التاجر كانت تعدّل اسم
     * الزبون وأصناف الطلب بنسخة قديمة ملتقطة وقت الفتح، وتمسح أي شي عمله الزبون بينهم.
     *
     * القاعدة في firestore.rules بتسمح لصاحب المتجر على حالتهم، فلو جاء معرّف لطلب
     * لغيره بيرفض السيرفر لا هنا.
     */
    public void setStatus(@NonNull String orderId, @NonNull String status,
                          @NonNull WriteCallback callback) {
        firestore.collection(Order.COLLECTION).document(orderId)
                .update(Order.FIELD_STATUS, status)
                .addOnCompleteListener(task -> finish(task, "setStatus " + status, callback));
    }

    /**
     * رفض الطلب: الحالة والسبب والملاحظة تُكتب مع بعض في عملية واحدة. لو انفصلوا،
     * لقطة تيجي بينهم تسيب طلب مرفوض بلا سبب، وشاشة الزبون ما تلقى شنو تحكيله.
     * والفراغ هنا مسموح: الملاحظة اختيارية كما في التصميم.
     */
    public void setRejected(@NonNull String orderId, @NonNull String reason,
                            @NonNull String note, @NonNull WriteCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Order.FIELD_STATUS, Order.STATUS_REJECTED);
        fields.put(Order.FIELD_REJECT_REASON, reason);
        fields.put(Order.FIELD_REJECT_NOTE, note);

        firestore.collection(Order.COLLECTION).document(orderId)
                .update(fields)
                .addOnCompleteListener(task -> finish(task, "setRejected " + reason, callback));
    }

    private void finish(@NonNull Task<Void> task, @NonNull String label,
                        @NonNull WriteCallback callback) {
        if (task.isSuccessful()) {
            Log.d(LOG_TAG, label + ": done");
            callback.onSuccess();
            return;
        }
        Log.e(LOG_TAG, label + " failed: " + messageOf(task.getException()));
        callback.onError();
    }

    private static String messageOf(Exception error) {
        return error == null || error.getMessage() == null ? "unknown" : error.getMessage();
    }

    @NonNull
    private static List<Order> ordersOf(@NonNull QuerySnapshot snapshot) {
        List<DocumentSnapshot> documents = new ArrayList<>(snapshot.getDocuments());
        // الأحدث أولاً في الكود لا في الاستعلام: شرط تساوي مع ترتيب على حقل آخر
        // يطلب فهرساً مركّباً في الكونسول، ونسيانه يوقف الشاشة كلها
        Collections.sort(documents, (left, right) ->
                Long.compare(placedAt(right), placedAt(left)));

        List<Order> orders = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            Order order = Order.fromMap(document.getId(), document.getData());
            order.setPlacedAt(placedAt(document));
            orders.add(order);
        }
        return orders;
    }

    /**
     * وقت الطلب يُقرأ من الـ Timestamp نفسه لا من الخريطة: Firestore بيحوّله لـ Date
     * عند getData، والنص اللي يطلّع منه صيغة إنجليزية حسب الطبع. والتاريخ اللي لسه
     * ما وصل من السيرفر يُصوَّر صفر، فيقعد آخر اللائحة لا في وسطها.
     */
    private static long placedAt(@NonNull DocumentSnapshot document) {
        Timestamp placed = document.getTimestamp(Order.FIELD_PLACED_AT);
        return placed == null ? 0 : placed.toDate().getTime();
    }
}
