package com.taqat.madar.ui.merchant.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.data.local.MerchantNotificationStore;
import com.taqat.madar.data.local.MerchantNotificationStore.Entry;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.databinding.ActivityMerchantNotificationsBinding;
import com.taqat.madar.databinding.ItemMerchantNotificationBinding;
import com.taqat.madar.ui.merchant.home.MerchantHomeActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * شاشة إشعارات التاجر، وتُفتح من الجرس في رأس المنيو ومن جرس الإعدادات.
 *
 * اللائحة تُقرأ من {@link MerchantNotificationStore} على الجهاز، فلا استماع هنا: الذي
 * يُنتج الإشعار هو {@link MerchantNotificationFeed} المركّب في واجهة التاجر نفسها،
 * والشاشة تقرأ ما كتبه وتعيد قراءته كل ما رجعت إليها من طلب أو من قسم عروض.
 *
 * التقسيم ثلاثة: الكل، والطلبات، والمتجر والنظام. والعدّاد على كل شريحة من قسمها وحده،
 * فلو حُسب من المعروض لانكسر الرقم لمّا يقرأ التاجر سطراً.
 */
public class MerchantNotificationsActivity extends AppCompatActivity {

    private ActivityMerchantNotificationsBinding binding;
    private final AuthRepository authRepository = new AuthRepository();

    private MerchantNotificationStore store;
    private NotificationAdapter adapter;

    /** القسم المختار الآن */
    private String shownType = MerchantNotificationStore.TYPE_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: الإشعارات ملك صاحب حساب، ولا معنى لشاشة بلا صاحب
            finish();
            return;
        }
        store = new MerchantNotificationStore(this, account.getUid());

        adapter = new NotificationAdapter();
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotifications.setAdapter(adapter);

        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            shownType = typeOfChip(checkedIds.get(0));
            reload();
        });

        binding.btnMarkAllRead.setOnClickListener(v -> {
            store.markAllRead();
            reload();
        });

        binding.btnClearPrevious.setOnClickListener(v -> {
            store.clearRead();
            reload();
        });

        // «الكل» مختارة كما في التصميم، والضبط قبل إعادة التحميل لأن المستمع يناديها
        binding.chipGroupFilter.check(R.id.chip_all);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // قد يكون التاجر قرأ سطراً من مكان آخر، أو وصل خبر وهو في تبويب غيره
        reload();
    }

    @NonNull
    private String typeOfChip(int chipId) {
        if (chipId == R.id.chip_orders) {
            return MerchantNotificationStore.TYPE_ORDER;
        }
        if (chipId == R.id.chip_store) {
            return MerchantNotificationStore.TYPE_STORE;
        }
        return MerchantNotificationStore.TYPE_ALL;
    }

    /** تعبئة اللائحة وأرقام الشرائح وأزرار السطر، كلها من قراءة واحدة للمتجر */
    private void reload() {
        if (store == null) {
            return;
        }
        List<Entry> entries = store.entries(shownType);
        adapter.submit(entries);

        binding.chipAll.setText(getString(R.string.notif_filter_all,
                store.count(MerchantNotificationStore.TYPE_ALL)));
        binding.chipOrders.setText(getString(R.string.notif_filter_orders,
                store.count(MerchantNotificationStore.TYPE_ORDER)));
        binding.chipStore.setText(getString(R.string.notif_filter_store,
                store.count(MerchantNotificationStore.TYPE_STORE)));

        binding.layoutEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvNotifications.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);

        // «تحديد الكل كمقروء» لا عمل له حين لا غير مقروء، وسطر المسح لا عمل له بلا مقروء
        binding.btnMarkAllRead.setVisibility(store.unreadCount() > 0 ? View.VISIBLE : View.INVISIBLE);
        binding.btnClearPrevious.setVisibility(hasRead() ? View.VISIBLE : View.INVISIBLE);
    }

    private boolean hasRead() {
        for (Entry entry : store.entries(MerchantNotificationStore.TYPE_ALL)) {
            if (entry.read) {
                return true;
            }
        }
        return false;
    }

    /**
     * ضغطة السطر تقرأه، والزر الذي فيه يقرأه ثم يذهب إلى موضعه: الطلب في تبويبه،
     * والعرض في قسم العروض. وما لا هدف له يقرأ نفسه فقط.
     */
    private void openEntry(@NonNull Entry entry) {
        store.markRead(entry.id);
        reload();

        int tab = tabOfKind(entry.kind);
        if (tab == 0) {
            return;
        }
        startActivity(new Intent(this, MerchantHomeActivity.class)
                .putExtra(MerchantHomeActivity.EXTRA_TAB, tab)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    /** التبويب الذي يفتح عليه هذا النوع، صفر لما لا مكان يذهب إليه */
    private int tabOfKind(@NonNull String kind) {
        if (MerchantNotificationStore.KIND_NEW_ORDER.equals(kind)) {
            return R.id.tab_orders;
        }
        if (MerchantNotificationStore.KIND_OFFER_ENDING.equals(kind)) {
            return R.id.tab_offers;
        }
        // TODO باقيا النوعين (وصول المندوب واكتمال الطلب) يفتحان تبويب الطلبات وقتها،
        //  فهما من مسار المندوب الذي لم يُبنَ بعد
        return 0;
    }

    private String price(double value) {
        return getString(R.string.currency_symbol) + String.format(Locale.US, "%.2f", value);
    }

    /** الوقت النسبي كما في بطاقة الطلب المصغّرة: هلقي، منذ دقيقة، منذ 10 دقائق… */
    private String relativeTime(long at) {
        if (at <= 0) {
            return "";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - at);
        if (minutes < 1) {
            return getString(R.string.order_ago_now);
        }
        if (minutes < 60) {
            return fmt(minutes == 1 ? R.string.order_ago_minute : R.string.order_ago_minutes,
                    minutes);
        }
        long hours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - at);
        if (hours < 24) {
            return fmt(hours == 1 ? R.string.order_ago_hour : R.string.order_ago_hours, hours);
        }
        long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - at);
        return fmt(days == 1 ? R.string.notif_ago_day : R.string.order_ago_days, days);
    }

    /** تفريع النصّ بـ Locale.US: الأرقام في التصميم لاتينية كما في بقية الشاشات */
    private String fmt(int resId, Object... args) {
        return String.format(Locale.US, getString(resId), args);
    }

    /** holder السطر: يمسك binding البطاقة ويبني محتواها على النوع وحالة القراءة */
    private final class NotificationHolder extends RecyclerView.ViewHolder {
        private final ItemMerchantNotificationBinding card;

        NotificationHolder(ItemMerchantNotificationBinding card) {
            super(card.getRoot());
            this.card = card;
        }

        void bind(@NonNull Entry entry) {
            card.tvTitle.setText(titleOf(entry));
            card.tvBody.setText(bodyOf(entry));
            card.tvTime.setText(relativeTime(entry.at));

            card.cardNotification.setCardBackgroundColor(ContextCompat.getColor(
                    MerchantNotificationsActivity.this,
                    entry.read ? R.color.white : R.color.notif_unread_bg));
            card.viewUnreadDot.setVisibility(entry.read ? View.GONE : View.VISIBLE);

            int icon = iconOf(entry.kind);
            card.ivIcon.setImageResource(icon);
            boolean warm = MerchantNotificationStore.KIND_NEW_ORDER.equals(entry.kind);
            card.boxIcon.setBackgroundResource(warm
                    ? R.drawable.bg_circle_peach : R.drawable.bg_icon_circle);
            card.ivIcon.setImageTintList(ContextCompat.getColorStateList(
                    MerchantNotificationsActivity.this,
                    warm ? R.color.primary_orange : R.color.secondary_navy));

            int action = actionLabelOf(entry.kind);
            card.btnAction.setVisibility(action == 0 ? View.GONE : View.VISIBLE);
            if (action != 0) {
                card.btnAction.setText(action);
                card.btnAction.setOnClickListener(v -> openEntry(entry));
            }

            card.cardNotification.setOnClickListener(v -> openEntry(entry));
        }

        private String titleOf(@NonNull Entry entry) {
            switch (entry.kind) {
                case MerchantNotificationStore.KIND_NEW_ORDER:
                    return entry.number > 0
                            ? fmt(R.string.notif_new_order_title, entry.number)
                            : getString(R.string.notif_new_order_title_plain);
                case MerchantNotificationStore.KIND_COURIER_ARRIVED:
                    return getString(R.string.notif_courier_arrived_title);
                case MerchantNotificationStore.KIND_ORDER_COMPLETED:
                    return fmt(R.string.notif_order_completed_title, entry.number);
                case MerchantNotificationStore.KIND_OFFER_ENDING:
                    return getString(R.string.notif_offer_ending_title);
                case MerchantNotificationStore.KIND_STORE_APPROVED:
                    return getString(R.string.notif_store_approved_title);
                default:
                    // نوع لا تعرفه هذه النسخة (من بناء أحدث): يُعرض نصّه كما هو بدل السقوط
                    return entry.name;
            }
        }

        private String bodyOf(@NonNull Entry entry) {
            switch (entry.kind) {
                case MerchantNotificationStore.KIND_NEW_ORDER:
                    return fmt(R.string.notif_new_order_body,
                            entry.name.isEmpty()
                                    ? getString(R.string.notif_unknown_customer) : entry.name,
                            price(entry.amount));
                case MerchantNotificationStore.KIND_COURIER_ARRIVED:
                    return fmt(R.string.notif_courier_arrived_body,
                            entry.name.isEmpty() ? getString(R.string.notif_unknown_customer)
                                    : entry.name, entry.number);
                case MerchantNotificationStore.KIND_ORDER_COMPLETED:
                    return getString(R.string.notif_order_completed_body);
                case MerchantNotificationStore.KIND_OFFER_ENDING:
                    return fmt(R.string.notif_offer_ending_body, entry.name.isEmpty()
                            ? getString(R.string.notif_unnamed_offer) : entry.name);
                case MerchantNotificationStore.KIND_STORE_APPROVED:
                    return getString(R.string.notif_store_approved_body);
                default:
                    return entry.name;
            }
        }

        private int iconOf(@NonNull String kind) {
            switch (kind) {
                case MerchantNotificationStore.KIND_NEW_ORDER:
                    return R.drawable.ic_notifications;
                case MerchantNotificationStore.KIND_COURIER_ARRIVED:
                    return R.drawable.ic_delivery;
                case MerchantNotificationStore.KIND_ORDER_COMPLETED:
                    return R.drawable.ic_check_circle;
                case MerchantNotificationStore.KIND_OFFER_ENDING:
                    return R.drawable.ic_volume_up;
                case MerchantNotificationStore.KIND_STORE_APPROVED:
                    return R.drawable.ic_verified_seal;
                default:
                    return R.drawable.ic_notifications_none;
            }
        }

        /** نص الزر، صفر لما لا هدف يفتحه السطر */
        private int actionLabelOf(@NonNull String kind) {
            switch (kind) {
                case MerchantNotificationStore.KIND_NEW_ORDER:
                    return R.string.notif_action_open_order;
                case MerchantNotificationStore.KIND_OFFER_ENDING:
                    return R.string.notif_action_open_offers;
                default:
                    return 0;
            }
        }
    }

    /**
     * لائحة البطاقات. تُستبدل بالكامل مع كل تحميل لأن عدد السطور يتغيّر بالقسم وبالقراءة
     * وبالمسح، والفرق بينها لا يحمل معنى يحفظ ترتيبه.
     */
    private final class NotificationAdapter extends RecyclerView.Adapter<NotificationHolder> {

        private final List<Entry> items = new ArrayList<>();

        void submit(@NonNull List<Entry> entries) {
            int size = items.size();
            if (size > 0) {
                notifyItemRangeRemoved(0, size);
                items.clear();
            }
            items.addAll(entries);
            notifyItemRangeInserted(0, items.size());
        }

        @NonNull
        @Override
        public NotificationHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new NotificationHolder(ItemMerchantNotificationBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
