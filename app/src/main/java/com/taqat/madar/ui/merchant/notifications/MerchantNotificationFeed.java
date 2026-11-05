package com.taqat.madar.ui.merchant.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.data.local.MerchantNotificationStore;
import com.taqat.madar.data.model.Offer;
import com.taqat.madar.data.model.Order;
import com.taqat.madar.data.remote.OfferRepository;
import com.taqat.madar.data.remote.OrderRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * إنتاج إشعارات التاجر من البيانات التي تصله، لا من سيرفر يدفعها.
 *
 * الشاشة تُقرأ من {@link MerchantNotificationStore}، وهذا الكائن هو كاتبها الوحيد داخل
 * التطبيق: يركّب مستمع الطلبات أول ما يدخل التاجر واجهته، ويزيله في onStop مثل بقية
 * المستمعات، فيبقى الإشعار يُولَّد والتاجر جالس لا بعد ما يقفل الصفحة.
 *
 * الطلبات القديمة لا تُشعِر: أول تشغيل يسجّل لحظة الآن مرجعاً، فما ورد قبلها موجود في
 * تبويب الطلبات ولا حاجة لتكراره. وما بعد المرجع يُشعَر به مرة واحدة، لأن المعرّف
 * محفوظ في اللائحة والإضافة تكراره تُرفض.
 */
public class MerchantNotificationFeed {

    /** التحذير قبل انتهاء العرض بيوم: مساحة كافية يحدّثه التاجر في نفس اليوم */
    private static final long OFFER_WARNING_MS = TimeUnit.DAYS.toMillis(1);

    private final MerchantNotificationStore store;
    private final String uid;
    private final OrderRepository orderRepository = new OrderRepository();
    private final OfferRepository offerRepository = new OfferRepository();

    /** آخر لحظة طلب أُشعِر بها، محفوظة على الجهاز فتقطع التسلسل بين التشغيلين */
    private long lastOrderSeen;

    private ListenerRegistration ordersWatch;

    public MerchantNotificationFeed(@NonNull Context context, @NonNull String uid) {
        this.uid = uid;
        this.store = new MerchantNotificationStore(context, uid);
    }

    /**
     * بدء المتابعة. مستمع الطلبات يوصل أول لقطة باللائحة كلها، فلو كان المرجع غير
     * محفوظ من قبل حُطّت الآن لا الصفر، وإلا انقلب أول فتح للتاجر إلى عاصفة إشعارات
     * عن طلبات قديمة.
     */
    public void start() {
        long baseline = store.lastOrderSeen();
        long now = System.currentTimeMillis();
        if (baseline < 0) {
            baseline = now;
            store.setLastOrderSeen(now);
        }
        lastOrderSeen = baseline;

        ordersWatch = orderRepository.watchForStore(uid, new OrderRepository.ListCallback() {
            @Override
            public void onOrders(@NonNull List<Order> orders) {
                notifyNewOrders(orders);
            }

            @Override
            public void onError() {
                // لا إشعار عن انقطاع: التاجر يرى رسالة التبويب، وتكرار الخبر لا يفيد
            }
        });

        checkEndingOffers();
    }

    public void stop() {
        if (ordersWatch != null) {
            ordersWatch.remove();
            ordersWatch = null;
        }
    }

    /**
     * طلب جديد ورد بعد المرجع: يُشعَر به بحالته لا بحالته اللاحقة، فالذي صار قيد
     * التحضير التاجر نفسه من حرّكه، وإشعاره له خبر قديم.
     */
    private void notifyNewOrders(@NonNull List<Order> orders) {
        long now = System.currentTimeMillis();
        long newest = lastOrderSeen;

        for (Order order : orders) {
            if (!Order.STATUS_NEW.equals(order.getStatus())) {
                continue;
            }
            // تاريخ ناقص (وثيقة كُتبت يدويًا في الكونسول) يُعامل كأنها وصلت الآن،
            // وإلا بقي صفراً تحت المرجع ولا يوصل التاجر خبره أبداً
            long at = order.getPlacedAt() > 0 ? order.getPlacedAt() : now;
            if (at <= lastOrderSeen) {
                continue;
            }

            store.add("order_" + order.getId(), MerchantNotificationStore.KIND_NEW_ORDER,
                    MerchantNotificationStore.TYPE_ORDER, at, order.getId(),
                    order.getCustomerName(), order.getNumber(), order.displayTotal());
            newest = Math.max(newest, at);
        }

        if (newest != lastOrderSeen) {
            lastOrderSeen = newest;
            store.setLastOrderSeen(newest);
        }
    }

    /**
     * عرضٌ ينتهي خلال يوم: تذكير واحد لكل عرض في اليوم، لأن المستمع يقرأ العرض كل مرة
     * ويصير التذكير اليومي منقوضاً. والمعرّف يحمل اليوم، فيعود التذكير بعد منتصفه إن
     * كان العرض ما زال على حاله.
     */
    private void checkEndingOffers() {
        offerRepository.loadForStore(uid, new OfferRepository.ListCallback() {
            @Override
            public void onOffers(@NonNull List<Offer> offers) {
                long now = System.currentTimeMillis();
                String day = String.valueOf(TimeUnit.MILLISECONDS.toDays(now));

                for (Offer offer : offers) {
                    long endsAt = offer.getEndsAt();
                    if (endsAt <= now || endsAt - now > OFFER_WARNING_MS) {
                        continue;
                    }
                    store.add("offer_ending_" + offer.getId() + "_" + day,
                            MerchantNotificationStore.KIND_OFFER_ENDING,
                            MerchantNotificationStore.TYPE_STORE, now, offer.getId(),
                            offer.getName(), 0, 0);
                }
            }

            @Override
            public void onError() {
                // التذكير رفاهية: فشل قراءته لا يُخبر التاجر بشيء، فالعروض نفسها تظهر له
            }
        });
    }

    /**
     * قرار الإدارة بالقبول يُسجّل وهو يصل شاشة الانتظار، لأن التاجر قد لا يعود لواجهته
     * إلا بعد يوم فيلقاه خبر الاعتماد في الإشعارات لا ضائعاً بين شاشتين.
     */
    public static void recordStoreApproved(@NonNull Context context, @NonNull String uid) {
        new MerchantNotificationStore(context, uid).add(
                "store_approved_" + uid, MerchantNotificationStore.KIND_STORE_APPROVED,
                MerchantNotificationStore.TYPE_STORE, System.currentTimeMillis(), "",
                "", 0, 0);
    }
}
