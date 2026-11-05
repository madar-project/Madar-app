package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.taqat.madar.data.model.Offer;

import java.util.ArrayList;
import java.util.List;

/**
 * كتابة العروض وقراءتها من Firestore.
 *
 * لا تاريخ إنشاء هنا ولا حالة: سريان العرض يُقرأ من تاريخيه المحفوظين، فالعرض الذي
 * انتهى لم يعد شيئاً يُحدَّث بل صفحة في سجلّ التاجر.
 */
public class OfferRepository {

    /** اسم فلتر في Logcat لمتابعة مشاكل العروض */
    public static final String LOG_TAG = "MadarOffers";

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public interface WriteCallback {
        void onSuccess();

        void onError();
    }

    /** عروض متجر واحد، بلا ترتيب: القارئ وحده يقرّر أيّها يعنيه */
    public interface ListCallback {
        void onOffers(@NonNull List<Offer> offers);

        void onError();
    }

    /**
     * نشر عرض جديد. وثيقة بمعرّف تُنشئه Firestore: عرضان من تاجر واحد لا يتصادمان،
     * ولو كتبنا المعرّف بأنفسنا لتعارض عرضان على الطبق نفسه في نفس الدقيقة.
     */
    public void create(@NonNull Offer offer, @NonNull WriteCallback callback) {
        if (offer.getStoreId().isEmpty()) {
            Log.e(LOG_TAG, "create refused: offer has no storeId");
            callback.onError();
            return;
        }

        firestore.collection(Offer.COLLECTION).document()
                .set(offer.toMap())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(LOG_TAG, "create: done");
                        callback.onSuccess();
                        return;
                    }
                    Log.e(LOG_TAG, "create failed: " + messageOf(task.getException()));
                    callback.onError();
                });
    }

    private static String messageOf(Exception error) {
        return error == null || error.getMessage() == null ? "unknown" : error.getMessage();
    }

    /**
     * قراءة عروض المتجر مرة واحدة لا مستمعاً: لا شاشة تعرض اللائحة بعد، والمطلوب منها
     * سؤال واحد — هل اقترب عرضٌ على الانتهاء فيُنبَّه التاجر. والترتيب فيها لا معنى له
     * لأن السائل يمشي على تاريخ النهاية لا على ترتيب الوصول.
     */
    public void loadForStore(@NonNull String storeId, @NonNull ListCallback callback) {
        firestore.collection(Offer.COLLECTION)
                .whereEqualTo(Offer.FIELD_STORE_ID, storeId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Log.e(LOG_TAG, "loadForStore failed: " + messageOf(task.getException()));
                        callback.onError();
                        return;
                    }

                    callback.onOffers(offersOf(task.getResult()));
                });
    }

    /**
     * عروض عدة متاجر في سؤال واحد: whereIn ما يقبل أكثر من عشرة معرّفات، فتُقسَم
     * اللائحة وتُجمَع نتائجها. شاشة تصفّح الزبون تسأل عن متاجر القسم كلّه لتضع
     * شارة «عرض» على صاحب العرض الساري، وقراءة متجر متجر كانت ستجعل لكل بطاقة رحلة.
     *
     * الجزء الفاشل لا يُبطل الباقي: شارة العرض للزينة لا أكثر، فتبقى البطاقات التي
     * وصلت عروضها معروضة لو سقطت رحلة واحدة.
     */
    public void loadForStores(@NonNull List<String> storeIds, @NonNull ListCallback callback) {
        if (storeIds.isEmpty()) {
            callback.onOffers(new ArrayList<>());
            return;
        }

        List<List<String>> chunks = chunksOf(storeIds);
        List<Offer> merged = new ArrayList<>();
        int[] pending = {chunks.size()};

        for (List<String> chunk : chunks) {
            firestore.collection(Offer.COLLECTION)
                    .whereIn(Offer.FIELD_STORE_ID, chunk)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            merged.addAll(offersOf(task.getResult()));
                        } else {
                            Log.e(LOG_TAG, "loadForStores failed: " + messageOf(task.getException()));
                        }
                        if (--pending[0] == 0) {
                            callback.onOffers(merged);
                        }
                    });
        }
    }

    @NonNull
    private static List<Offer> offersOf(@NonNull QuerySnapshot snapshot) {
        List<Offer> offers = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            offers.add(Offer.fromMap(document.getId(), document.getData()));
        }
        return offers;
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
}
