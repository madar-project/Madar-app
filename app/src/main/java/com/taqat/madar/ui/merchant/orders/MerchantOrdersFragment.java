package com.taqat.madar.ui.merchant.orders;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.R;
import com.taqat.madar.data.model.Order;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.OrderRepository;
import com.taqat.madar.databinding.FragmentMerchantOrdersBinding;
import com.taqat.madar.databinding.ItemOrderCompactBinding;
import com.taqat.madar.databinding.ItemOrderFullBinding;
import com.taqat.madar.databinding.ItemOrderLineBinding;
import com.taqat.madar.databinding.SheetRejectOrderBinding;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * تبويب الطلبات عند التاجر: طلبات متجره تصل لحظة بلحظة، أجددها في بطاقة مفتوحة
 * بأصنافها ومجموعها، وبعده بطاقات مصغّرة لما هو جاري معه.
 *
 * الشرائح في الأعلى عددٌ لا أكثر: «جديد (3)» و«قيد التحضير (2)» كي يعرف قبل ما
 * يفتح كم طلب ناظر ردّه. و«الأرشيف» رقم يبيّن كم خلّص، لا شاشة: شاشة المرفوض
 * والمكتمل ما سلّمتها بعد، فما نركّب ضغطة ما وراها شي.
 *
 * للتاجر على الطلب فعلين وحدهم كما قررت: قبول يحوّله قيد التحضير، ورفض يطلّعه
 * من اللائحة. والرفض ما بيمشيش بضغطة — بيفتح نافذة السبب أول، لأن الزبون لازم يعرف
 * ليه، والتقييم السلبي بييجي من طلب مرفوض بلا كلمة.
 *
 * البطاقة اللي ما عادت جديدة ما بيلاقي عليها زرارين: الحالة اللي فيها مش شغله،
 * وزرار كان يحرّكها بلا صاحب.
 */
public class MerchantOrdersFragment extends Fragment {

    private final AuthRepository authRepository = new AuthRepository();
    private final OrderRepository orderRepository = new OrderRepository();

    private FragmentMerchantOrdersBinding binding;
    private String uid = "";
    private ListenerRegistration ordersWatch;

    /** كل طلبات المتجر غير المؤرشفة، الأحدث أولاً كما رتّبها المستودع */
    private final List<Order> activeOrders = new ArrayList<>();

    /** "" جديد، و Order.STATUS_PREPARING ما بعده. قيمتان لا أكثر لأن العمل الجديد وحده */
    private String selectedStatus = Order.STATUS_NEW;

    /** بصمة عدّادات الشرائح: ما تغيّر عدد ما تُعاد بنائها ولا تُسقط المختارة */
    private String chipsSignature = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMerchantOrdersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: مقدرش نعرّف مين التاجر، فمفيش طلبات متجر نُعرضها
            Toast.makeText(requireContext(), R.string.error_account_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        uid = account.getUid();

        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chosen = group.findViewById(checkedIds.get(0));
            selectedStatus = chosen == null || chosen.getTag() == null
                    ? Order.STATUS_NEW : chosen.getTag().toString();
            renderList();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!uid.isEmpty()) {
            watchOrders();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (ordersWatch != null) {
            ordersWatch.remove();
            ordersWatch = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        // الحاوية الجديدة فاضية: بصمة الشرائح لازم تتمسح، وإلا بناء الشرائح يحسب
        // إن ما تغيّرش شي فلا تُركَّب شريحة ولا تتعبّأ اللائحة
        chipsSignature = "";
    }

    /**
     * متابعة طلبات المتجر. اللقطة تصل باللائحة كاملة مرتّبة، فالشاشة ترسمها كما
     * هي ولا تحتفظ بنسخة تعدّلها حالها: المصدر واحد، وما في حالة معروضة غير الحالة
     * اللي على السيرفر.
     */
    private void watchOrders() {
        ordersWatch = orderRepository.watchForStore(uid, new OrderRepository.ListCallback() {
            @Override
            public void onOrders(@NonNull List<Order> orders) {
                runOnUi(() -> {
                    if (binding == null) {
                        return;
                    }
                    activeOrders.clear();
                    for (Order order : orders) {
                        if (!order.isArchived()) {
                            activeOrders.add(order);
                        }
                    }
                    buildChips(orders);
                    renderList();
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> {
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.orders_load_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /**
     * شريحتان بعدّاديهما. «قيد التحضير» تجمع كل ما عدّا مرحلة الجديد وما أرشف بعد،
     * لا حالة واحدة اسمها preparing: لو مسارات ثانية (جاهز، مكتمل) كتبت حالتها يومًا،
     * الطلب يضل قدام التاجر ما وراه ضايع في شريحة ما موجودة.
     */
    private void buildChips(@NonNull List<Order> orders) {
        int fresh = countOf(true);
        int running = countOf(false);
        int archive = 0;
        for (Order order : orders) {
            if (order.isArchived()) {
                archive++;
            }
        }

        String signature = fresh + "|" + running + "|" + archive;
        binding.tvArchive.setText(fmt(R.string.orders_archive_link, archive));

        if (signature.equals(chipsSignature)) {
            return;
        }
        chipsSignature = signature;

        binding.chipGroupStatus.removeAllViews();
        Chip freshChip = addChip(Order.STATUS_NEW, fmt(R.string.orders_filter_new, fresh));
        Chip runningChip = addChip(Order.STATUS_PREPARING,
                fmt(R.string.orders_filter_preparing, running));

        // المختارة تُرجع بعد البناء: الشرائح اتهدّت واتركّبت من جديد، ولو خليتها
        // على حالها لسقط اختيار التاجر ورجع للبطاقة الأولى. و«جديد» اتفرّغ وهو
        // المختار: ننتقل للتانية وإلا شاشة فاضية بلا سبب ظاهر قدامه.
        if (Order.STATUS_PREPARING.equals(selectedStatus) || (fresh == 0 && running > 0)) {
            selectedStatus = Order.STATUS_PREPARING;
            binding.chipGroupStatus.check(runningChip.getId());
        } else {
            selectedStatus = Order.STATUS_NEW;
            binding.chipGroupStatus.check(freshChip.getId());
        }
    }

    private Chip addChip(@NonNull String status, @NonNull String label) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_chip, binding.chipGroupStatus, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(status);
        binding.chipGroupStatus.addView(chip);
        return chip;
    }

    /** عدد الجارية في الشريحة المختارة: true الجديد، false ما عدّاه */
    private int countOf(boolean isNew) {
        int count = 0;
        for (Order order : activeOrders) {
            if (order.isAwaitingMerchant() == isNew) {
                count++;
            }
        }
        return count;
    }

    /**
     * اللائحة تُرسم من الصفر مع كل لقطة: بطاقات الطلب نصّ وحده بلا صور تُنزّل،
     * فبناؤها رخيص، والتوفير اللي عمله المنيو كان سببه Glide لا العدد.
     * والإعادة من الصفر تقطع من جذرها خطأ الأب المزدوج: الحاوية تتفرّغ قبل الإضافة.
     */
    private void renderList() {
        binding.llFeatured.removeAllViews();
        binding.llOther.removeAllViews();

        List<Order> visible = new ArrayList<>();
        for (Order order : activeOrders) {
            if (order.isAwaitingMerchant() == Order.STATUS_NEW.equals(selectedStatus)) {
                visible.add(order);
            }
        }

        if (!visible.isEmpty()) {
            bindFeatured(visible.get(0));
        }
        if (visible.size() > 1) {
            for (int index = 1; index < visible.size(); index++) {
                bindCompact(visible.get(index));
            }
        }

        binding.llOtherHeader.setVisibility(visible.size() > 1 ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setText(emptyMessage());
    }

    /** نصّ اللائحة الفاضية: الفارغ اللي عنده طلبات غير المختارة غير الفاضي من كل شي */
    private String emptyMessage() {
        if (activeOrders.isEmpty()) {
            return getString(R.string.orders_empty_all);
        }
        return Order.STATUS_NEW.equals(selectedStatus)
                ? getString(R.string.orders_empty_new)
                : getString(R.string.orders_empty_preparing);
    }

    /** البطاقة المفتوحة: الرأس والأصناف والمجموع والفعلين لو الطلب لسه جديد */
    private void bindFeatured(@NonNull Order order) {
        ItemOrderFullBinding card = ItemOrderFullBinding.inflate(
                LayoutInflater.from(requireContext()), binding.llFeatured, false);

        card.tvNumber.setText(fmt(R.string.order_number, order.getNumber()));
        card.tvBadge.setText(order.isAwaitingMerchant()
                ? getString(R.string.order_badge_new) : statusLabel(order.getStatus()));
        card.tvName.setText(order.getCustomerName());
        card.tvTime.setText(absoluteTime(order.getPlacedAt()));
        card.tvTotal.setText(price(order.displayTotal()));

        for (Order.Line line : order.getLines()) {
            card.llLines.addView(lineView(line));
        }

        boolean waiting = order.isAwaitingMerchant();
        card.llActions.setVisibility(waiting ? View.VISIBLE : View.GONE);

        // البطاقة المفتوحة تُبنى من جديد مع كل لقطة، فالمستمع يتصل بها مرة واحدة
        // كفاية؛ والمعرّف على الوسم لا في حقل: نسخة الطلب اللي في closure الزرار
        // بتقادم، واللقطة ممكن تكون بدّلت الحالة بين الضغط والكتابة
        card.btnCall.setTag(order.getId());
        card.btnCall.setOnClickListener(this::callCustomer);

        if (waiting) {
            card.btnAccept.setTag(order.getId());
            card.btnAccept.setOnClickListener(v ->
                    changeStatus(v, Order.STATUS_PREPARING, R.string.order_accepted_toast));
            card.btnReject.setTag(order.getId());
            card.btnReject.setOnClickListener(v -> askRejectReason(v));
        }

        binding.llFeatured.addView(card.getRoot());
    }

    /** سطر صنف واحد: الكمية، الاسم وفوقها ملاحظة الزبون لو كتب واحدة، والسعر */
    @NonNull
    private View lineView(@NonNull Order.Line line) {
        ItemOrderLineBinding row = ItemOrderLineBinding.inflate(
                LayoutInflater.from(requireContext()), binding.llFeatured, false);
        row.tvQuantity.setText(fmt(R.string.order_line_quantity, line.getQuantity()));
        row.tvName.setText(line.getName());
        row.tvPrice.setText(price(line.getUnitPrice() * line.getQuantity()));
        // الملاحظة تختفي لو فاضية: سطر فاضي بين صنفين بيكسّر اللائحة على غير سبب
        row.tvNote.setVisibility(line.getNote().isEmpty() ? View.GONE : View.VISIBLE);
        row.tvNote.setText(line.getNote());
        return row.getRoot();
    }

    /** البطاقة المصغّرة: سطر واحد للطلب، وفعلين لو جديد، وحبّة حالة لو لا */
    private void bindCompact(@NonNull Order order) {
        ItemOrderCompactBinding card = ItemOrderCompactBinding.inflate(
                LayoutInflater.from(requireContext()), binding.llOther, false);

        card.tvNumber.setText(fmt(R.string.order_number, order.getNumber()));
        card.tvName.setText(order.getCustomerName());
        card.tvLinesCount.setText(fmt(R.string.order_lines_count, order.getLineCount()));
        card.tvPrice.setText(price(order.displayTotal()));

        String when = relativeTime(order.getPlacedAt());
        card.tvSubLine.setText(order.getDelivery().isEmpty()
                ? when : fmt(R.string.order_sub_line, when, order.getDelivery()));

        boolean waiting = order.isAwaitingMerchant();
        card.btnAccept.setVisibility(waiting ? View.VISIBLE : View.GONE);
        card.btnReject.setVisibility(waiting ? View.VISIBLE : View.GONE);
        card.tvStatusPill.setVisibility(waiting ? View.GONE : View.VISIBLE);
        if (!waiting) {
            card.tvStatusPill.setText(statusLabel(order.getStatus()));
        }

        card.btnAccept.setTag(order.getId());
        card.btnAccept.setOnClickListener(v ->
                changeStatus(v, Order.STATUS_PREPARING, R.string.order_accepted_toast));
        card.btnReject.setTag(order.getId());
        card.btnReject.setOnClickListener(v -> askRejectReason(v));

        binding.llOther.addView(card.getRoot());
    }

    /** اسم الحالة بالعربي، وقيمة محفوظة ما بتتنعرضش على الوجه */
    private String statusLabel(@NonNull String status) {
        if (Order.STATUS_PREPARING.equals(status)) {
            return getString(R.string.order_status_preparing);
        }
        if (Order.STATUS_READY.equals(status)) {
            return getString(R.string.order_status_ready);
        }
        if (Order.STATUS_COMPLETED.equals(status)) {
            return getString(R.string.order_status_completed);
        }
        if (Order.STATUS_REJECTED.equals(status)) {
            return getString(R.string.order_status_rejected);
        }
        return getString(R.string.order_badge_new);
    }

    /**
     * القبول والرفض: تحديث جزئي على الحقل وحده، والنجاح بييجي من اللقطة نفسها لا
     * من هنا — الطلب الجديد بيطلع من شريحة لحد الأرشيف، فالسيرفر بيحكي للشاشة.
     * والفشل بيقول فقط: ما بدنا نبدّل حالة ما اتكتبتش فيعرض التاجر طلبًا على إنه خلّص.
     */
    private void changeStatus(@NonNull View button, @NonNull String status, int successToast) {
        Object tag = button.getTag();
        if (!(tag instanceof String)) {
            return;
        }
        String orderId = (String) tag;

        button.setEnabled(false);
        orderRepository.setStatus(orderId, status, new OrderRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                runOnUi(() -> {
                    if (binding == null) {
                        return;
                    }
                    toast(successToast);
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> {
                    if (binding == null) {
                        return;
                    }
                    // الزرار ممكن يكون طار مع اللائحة اللي اتبنّت من جديد، فالرجوع
                    // عليه مشروط: ما بنلمش شي راح
                    if (button.getParent() != null) {
                        button.setEnabled(true);
                    }
                    toast(R.string.order_action_failed);
                });
            }
        });
    }

    /**
     * نافذة سبب الرفض: تطلع من تحت، والسبب الأول مختار على طول زي الرسمة. الطلب
     * بيُقرأ من اللائحة الحالية بمعرّفه لا من نسخة ملتقطة وقت الرسم، ولو كان طار
     * وهو الزرار ضاغط ما بنفتحش نافذة على طلب مش موجود.
     */
    private void askRejectReason(@NonNull View button) {
        Object tag = button.getTag();
        Order order = tag instanceof String ? orderById((String) tag) : null;
        if (order == null) {
            return;
        }

        SheetRejectOrderBinding sheet = SheetRejectOrderBinding.inflate(
                LayoutInflater.from(requireContext()));
        sheet.tvRefund.setText(fmt(R.string.reject_refund_note, customerName(order)));

        final View[] rows = {sheet.rowStock, sheet.rowBusy, sheet.rowClosed, sheet.rowOther};
        final ImageView[] radios = {sheet.ivStock, sheet.ivBusy, sheet.ivClosed, sheet.ivOther};
        final TextView[] notes = {sheet.tvStockNote, sheet.tvBusyNote,
                sheet.tvClosedNote, sheet.tvOtherNote};
        final String[] codes = {Order.REASON_OUT_OF_STOCK, Order.REASON_BUSY,
                Order.REASON_CLOSED, Order.REASON_OTHER};

        // السبب في مصفوفة واحدة لا في حقل للكائن: النافذة بتعيش دقايق، والحقل كان
        // يضل مربوط بطلب بعد ما يتغيّر أو يطلع من اللائحة
        final String[] reason = {Order.REASON_OUT_OF_STOCK};
        for (int index = 0; index < rows.length; index++) {
            final int position = index;
            rows[index].setOnClickListener(v -> {
                reason[0] = codes[position];
                paintReasons(rows, radios, notes, position);
            });
        }
        paintReasons(rows, radios, notes, 0);

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheet.getRoot());

        // الاستدارة على الحاوية اللي جوّاها المحتوى لا على المحتوى نفسه: الإطار
        // الأبيض اللي بيلفّه هو صاحب الحواف، ولو استديرنا جواه بيفضل مربّع
        dialog.setOnShowListener(shown -> {
            View frame = (View) sheet.getRoot().getParent();
            if (frame == null) {
                return;
            }
            frame.setBackgroundResource(R.drawable.bg_sheet_top);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(frame);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        if (dialog.getWindow() != null) {
            // الكيبورد يرفع النافذة فوقه، ما يغطّيش خانة الملاحظة
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        sheet.btnCancel.setOnClickListener(v -> dialog.dismiss());
        sheet.btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            reject(order.getId(), reason[0], sheet.etNote.getText().toString().trim());
        });
        dialog.show();
    }

    /** الحد والشارة ولون السطر التاني: التلاتة قرار واحد، فما ينقسموش على حالين */
    private void paintReasons(@NonNull View[] rows, @NonNull ImageView[] radios,
                               @NonNull TextView[] notes, int selected) {
        int orange = ContextCompat.getColor(requireContext(), R.color.primary_orange);
        int grey = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        for (int index = 0; index < rows.length; index++) {
            boolean chosen = index == selected;
            rows[index].setBackgroundResource(chosen
                    ? R.drawable.bg_option_card_selected : R.drawable.bg_option_card);
            radios[index].setImageResource(chosen
                    ? R.drawable.ic_radio_on : R.drawable.ic_radio_off);
            notes[index].setTextColor(chosen ? orange : grey);
        }
    }

    /**
     * كتابة الرفض: الحالة والسبب والملاحظة في عملية واحدة، واللائحة بتتحدّث من
     * اللقطة لا من هنا. والفشل بيقول بس: ما بنحطش حالة ما اتكتبتش.
     */
    private void reject(@NonNull String orderId, @NonNull String reason, @NonNull String note) {
        orderRepository.setRejected(orderId, reason, note, new OrderRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                runOnUi(() -> toast(R.string.order_rejected_toast));
            }

            @Override
            public void onError() {
                runOnUi(() -> toast(R.string.order_action_failed));
            }
        });
    }

    /** اسم الزبون لسطر الاسترجاع، ولو ما مكتوبش يبقى السطر مقروء */
    private String customerName(@NonNull Order order) {
        String name = order.getCustomerName().trim();
        return name.isEmpty() ? getString(R.string.reject_customer_fallback) : name;
    }

    /** الطلب الحالي بمعرّفه، أو null إن اترفض أو اكتمل وهو معروض */
    @Nullable
    private Order orderById(@NonNull String orderId) {
        for (Order order : activeOrders) {
            if (orderId.equals(order.getId())) {
                return order;
            }
        }
        return null;
    }

    private void toast(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /** الاتصال بالزبون: طلب التاجر على رقم محمول، فنتصل لا نرسل SMS */
    private void callCustomer(@NonNull View button) {
        Object tag = button.getTag();
        Order order = tag instanceof String ? orderById((String) tag) : null;
        String phone = order == null ? "" : order.getCustomerPhone().trim();

        if (phone.isEmpty()) {
            toast(R.string.order_no_phone);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        } catch (android.content.ActivityNotFoundException missing) {
            toast(R.string.order_no_phone);
        }
    }

    // ==================== الوقت والسعر ====================

    /**
     * الوقت المطلق للبطاقة المفتوحة: «اليوم 12:40 م»، «أمس 09:15 ص»، وما عدّى
     * يومين يرجع نسبيًا (منذ 4 أيام) لأن التاريخ الطويل ما بيضيف للتاجر شي.
     */
    private String absoluteTime(long placedAt) {
        if (placedAt <= 0) {
            return "";
        }
        long days = TimeUnit.MILLISECONDS.toDays(todayStart() - dayStart(placedAt));
        if (days == 0) {
            return fmt(R.string.order_time_today, clockOf(placedAt));
        }
        if (days == 1) {
            return fmt(R.string.order_time_yesterday, clockOf(placedAt));
        }
        return relativeTime(placedAt);
    }

    /** الوقت النسبي للبطاقة المصغّرة: هلقي، منذ دقيقة، منذ 6 دقائق، منذ ساعتين… */
    private String relativeTime(long placedAt) {
        if (placedAt <= 0) {
            return "";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(
                System.currentTimeMillis() - placedAt);
        if (minutes < 1) {
            return getString(R.string.order_ago_now);
        }
        if (minutes < 60) {
            return fmt(minutes == 1 ? R.string.order_ago_minute : R.string.order_ago_minutes,
                    minutes);
        }
        long hours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - placedAt);
        if (hours < 24) {
            return fmt(hours == 1 ? R.string.order_ago_hour : R.string.order_ago_hours, hours);
        }
        return fmt(R.string.order_ago_days, TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - placedAt));
    }

    /** الساعة بصيغة 12 مع ص/م، كما في التصميم لا 24 */
    private String clockOf(long placedAt) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(placedAt);
        int hour = calendar.get(Calendar.HOUR);
        int minute = calendar.get(Calendar.MINUTE);
        String meridiem = calendar.get(Calendar.AM_PM) == Calendar.AM
                ? getString(R.string.order_am) : getString(R.string.order_pm);
        String digits = String.format(Locale.US, "%d:%02d", hour == 0 ? 12 : hour, minute);
        return fmt(R.string.order_clock, digits, meridiem);
    }

    private long todayStart() {
        return dayStart(System.currentTimeMillis());
    }

    private long dayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String price(double value) {
        return getString(R.string.currency_symbol) + String.format(Locale.US, "%.2f", value);
    }

    /**
     * تفريع النصّ بـ Locale.US: الأرقام في التصميم لاتينية (‎#2088‎، ₪11.50)، ولو
     * تركناها للغة الجهاز تحوّلت لأرقام هندية (٢٠٨٨) تخالف الرسمة.
     */
    private String fmt(int resId, Object... args) {
        return String.format(Locale.US, getString(resId), args);
    }

    /** تنفيذ على خيط الواجهة إن كانت الصفحة ما زالت متصلة بنشاطها */
    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }
}
