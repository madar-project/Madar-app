package com.taqat.madar.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * إشعارات التاجر محفوظة على جهازه.
 *
 * لا سيرفر خلف هذه اللائحة بعد: إشعار يصل والتطبيق مقفول يحتاج خدمة دفع (FCM) ولم
 * تُوصل بعد، فما يُنتج هنا يُولَّد من البيانات التي تصل التطبيق وهو مفتوح — طلب وصل،
 * عرض اقترب انتهاءه، قرار إدارة مدار. وبقاء النص على الجهاز لا في Firestore هو الفرق بين
 * «قرأتها» و«ما قرأتهاش»: حالة القراءة خاصة بهاتف التاجر وحده، ولو كُتبت في السيرفر
 * لتعدّى على وثيقة لا يملك التاجر كتابتها أصلاً.
 *
 * كل سطر بمعرّفه الفريد: المنتج ينادي add بنفس المعرّف كل ما تصل لقطة، والموجود يُتجاهل،
 * فلا يتكاثر إشعار الطلب الواحد مع كل تغيير في اللائحة.
 */
public class MerchantNotificationStore {

    /** قسم «الطلبات»: كل ما جاء من مسار الطلبات — جديد، مندوب، اكتمال */
    public static final String TYPE_ORDER = "order";
    /** قسم «المتجر والنظام»: قرار الإدارة وتذكير العروض وتنبيهات حالة المتجر */
    public static final String TYPE_STORE = "store";
    /** بلا فلتر: كل الأقسام في لائحة واحدة */
    public static final String TYPE_ALL = "all";

    public static final String KIND_NEW_ORDER = "new_order";
    public static final String KIND_OFFER_ENDING = "offer_ending";
    public static final String KIND_STORE_APPROVED = "store_approved";
    // TODO المسارَان التاليان بلا كاتِب بعد: «المندوب وصل» و«تم الاكتمال» يُولّدان لمّا
    //  يشتغل مسار المندوب. الخريطة عليهما في الشاشة جاهزة، فيكفي وقتها سطر إنتاج واحد.
    public static final String KIND_COURIER_ARRIVED = "courier_arrived";
    public static final String KIND_ORDER_COMPLETED = "order_completed";

    /**
     * أعلى عدد يبقى محفوظاً: الأقدم يُحذف أول. واللائحة بلا سقف تكبر على الجهاز سنة كاملة
     * حتى لو ما فتحها أحد، ومسح المقروء وحده ما بيكفي لأن غير المقروء ما يمسحه.
     */
    private static final int MAX_ENTRIES = 60;

    private static final String PREFS = "merchant_notifications";
    private static final String KEY_LIST = "list_";
    private static final String KEY_SEEN_ORDERS = "seen_orders_";

    private final SharedPreferences prefs;
    private final String keyList;
    private final String keySeenOrders;

    /** سطر واحد في اللائحة. الحقول النهائية لأن التعديل يُنتج سطراً جديداً مكان القديم */
    public static final class Entry {
        public final String id;
        public final String kind;
        public final String type;
        /** لحظة وصول الخبر، وعليها يُحسب «منذ 10 دقائق» */
        public final long at;
        /** معرّف ما يفتحه السطر (طلب مثلاً)، فاضي لما لا هدف له */
        public final String refId;
        /** اسم في النص: عميل أو عرض */
        public final String name;
        /** رقم الطلب كما يراه الزبون، صفر لما لا رقم */
        public final int number;
        /** قيمة الطلب بالشيكل، صفر لما لا قيمة */
        public final double amount;
        public final boolean read;

        Entry(String id, String kind, String type, long at, String refId, String name,
              int number, double amount, boolean read) {
            this.id = id;
            this.kind = kind;
            this.type = type;
            this.at = at;
            this.refId = refId == null ? "" : refId;
            this.name = name == null ? "" : name;
            this.number = number;
            this.amount = amount;
            this.read = read;
        }
    }

    public MerchantNotificationStore(@NonNull Context context, @NonNull String uid) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        keyList = KEY_LIST + uid;
        keySeenOrders = KEY_SEEN_ORDERS + uid;
    }

    /**
     * إضافة سطر. يُرجع false إن كان معرّفه محفوظاً قبل، فيعرف المنتج أن هذا الخبر سبقه.
     */
    public boolean add(@NonNull String id, @NonNull String kind, @NonNull String type,
                       long at, String refId, String name, int number, double amount) {
        List<Entry> entries = entries(TYPE_ALL);
        for (Entry existing : entries) {
            if (existing.id.equals(id)) {
                return false;
            }
        }

        entries.add(new Entry(id, kind, type, at, refId, name, number, amount, false));
        // الأحدث أولاً قبل الحفظ: الشاشة تقرأ اللائحة كما هي، فلا تُعيد ترتيبها كل عرض
        Collections.sort(entries, newestFirst());
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        save(entries);
        return true;
    }

    /** اللائحة بالأحدث أولاً، وقسم واحد عند وجوده */
    @NonNull
    public List<Entry> entries(@NonNull String type) {
        List<Entry> all = read();
        if (TYPE_ALL.equals(type)) {
            return all;
        }
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.type.equals(type)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public int count(@NonNull String type) {
        return entries(type).size();
    }

    /** عدد غير المقروء: نقطة الجرس المصمت في رأس المنيو */
    public int unreadCount() {
        int unread = 0;
        for (Entry entry : read()) {
            if (!entry.read) {
                unread++;
            }
        }
        return unread;
    }

    /** تحديد الكل كمقروء: السطر المقروء يُكتب كما هو بلا تغيير في نصه */
    public void markAllRead() {
        List<Entry> entries = read();
        List<Entry> updated = new ArrayList<>();
        for (Entry entry : entries) {
            updated.add(entry.read ? entry
                    : new Entry(entry.id, entry.kind, entry.type, entry.at, entry.refId,
                    entry.name, entry.number, entry.amount, true));
        }
        save(updated);
    }

    /**
     * قراءة سطر واحد: الضغطة على البطاقة تقرأها فتزول النقطة وتفتح البطاقة البيضاء
     * مكان الخوخية، وبمعرّفها وحده لا بموضعها لأن الموضع يتغيّر بالتقسيم وبالمسح.
     */
    public void markRead(@NonNull String id) {
        List<Entry> entries = read();
        List<Entry> updated = new ArrayList<>();
        for (Entry entry : entries) {
            updated.add(!entry.id.equals(id) || entry.read ? entry
                    : new Entry(entry.id, entry.kind, entry.type, entry.at, entry.refId,
                    entry.name, entry.number, entry.amount, true));
        }
        save(updated);
    }

    /**
     * مسح الإشعارات السابقة: المقروء وحده يذهب، وغير المقروء يبقى لأن التاجر ما شافه
     * بعد، وضغطة واحدة كانت تمحو له طلبات منتظرته.
     */
    public void clearRead() {
        List<Entry> entries = read();
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.read) {
                kept.add(entry);
            }
        }
        save(kept);
    }

    /**
     * آخر لحظة طلب أُشعِر بها التاجر، سالب أول مرة لأن اللائحة السابقة ما تُعرف:
     * أول تشغيل يسجّل الوقت الحالي بلا إشعارات عن طلبات قديمة، واللقطات بعده تُقارَن به.
     */
    public long lastOrderSeen() {
        return prefs.getLong(keySeenOrders, -1L);
    }

    public void setLastOrderSeen(long millis) {
        prefs.edit().putLong(keySeenOrders, millis).apply();
    }

    @NonNull
    private Comparator<Entry> newestFirst() {
        return (left, right) -> Long.compare(right.at, left.at);
    }

    @NonNull
    private List<Entry> read() {
        List<Entry> entries = new ArrayList<>();
        String raw = prefs.getString(keyList, "");
        if (TextUtils.isEmpty(raw)) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                entries.add(new Entry(
                        object.optString("id"),
                        object.optString("kind"),
                        object.optString("type", TYPE_STORE),
                        object.optLong("at"),
                        object.optString("refId"),
                        object.optString("name"),
                        object.optInt("number"),
                        object.optDouble("amount", 0),
                        object.optBoolean("read")));
            }
        } catch (JSONException error) {
            // لائحة تالفة (ترقية غيرت الصيغة مثلاً) تُبدأ من جديد، ولا تُوقف الشاشة
            prefs.edit().remove(keyList).apply();
            return new ArrayList<>();
        }
        Collections.sort(entries, newestFirst());
        return entries;
    }

    private void save(@NonNull List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.id);
                object.put("kind", entry.kind);
                object.put("type", entry.type);
                object.put("at", entry.at);
                object.put("refId", entry.refId);
                object.put("name", entry.name);
                object.put("number", entry.number);
                object.put("amount", entry.amount);
                object.put("read", entry.read);
            } catch (JSONException ignored) {
                // حقل نصي لا يقبل null وقد مرّ أعلاه، فالحذف أسهل من تعكير اللائحة
                continue;
            }
            array.put(object);
        }
        // apply لا commit: الكتابة تُجدَّل على خيط خلفي، والإشعارات تُنتَج وتُحدَّث
        // وخيط الواجهة يقرأ اللقطة، فانتظار القرص كان يقطع الحركة بلا سبب
        prefs.edit().putString(keyList, array.toString()).apply();
    }
}
