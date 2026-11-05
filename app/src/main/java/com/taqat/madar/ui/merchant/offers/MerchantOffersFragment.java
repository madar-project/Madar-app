package com.taqat.madar.ui.merchant.offers;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.R;
import com.taqat.madar.data.model.Offer;
import com.taqat.madar.data.model.Product;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.OfferRepository;
import com.taqat.madar.data.remote.ProductRepository;
import com.taqat.madar.databinding.FragmentMerchantOffersBinding;
import com.taqat.madar.databinding.ItemCategoryOptionBinding;
import com.taqat.madar.databinding.ItemOfferProductBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * تبويب العروض: نموذج إنشاء عرض واحد، يفتح كلما داس التاجر التبويب.
 *
 * العرض لا يغيّر منتجاً ولا سعراً: هو وثيقة مستقلة تحمل معرّفات الأصناف المشمولة،
 * والخصم يُحسب حين يُعرض. فما في هنا تعديل على products إطلاقاً، وحذف العرض يُبقي
 * المنيو كما كان.
 *
 * الأصناف تُقرأ من منيو التاجر نفسه متابعةً حيّة: عرض على طبق حذفه من قائمة
 * المشمولة لا معنى له، فلا يبقى معرّفه معلّقاً في النموذج.
 */
public class MerchantOffersFragment extends Fragment {

    /** يوم كامل بالمللي ثانية، تُبنى عليه تواريخ العرض من أول ثانية إلى آخر ثانية */
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    /** مدّة افتراضية للعرض: أسبوع، فالتاجر الذي يغيّر أسعاره كل جمعة لا يغيّر تواريخه كل يوم */
    private static final int DEFAULT_SPAN_DAYS = 7;

    private final AuthRepository authRepository = new AuthRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private final OfferRepository offerRepository = new OfferRepository();

    /** التاريخ كما في التصميم: سنة/شهر/يوم بأرقام لاتينية، لا اسم شهر ولا ترجمة */
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy/MM/dd", Locale.US);

    /** يُكتب في خانتي التاريخ، ويُحفظ في الوثيقة مللي ثوانٍ */
    private long startsAt;
    private long endsAt;

    private FragmentMerchantOffersBinding binding;
    private String uid = "";
    private ListenerRegistration productsWatch;

    /**
     * لوحة التصنيفات النازلة: متغيّرة لأن ضغطة ثانية وهي ظاهرة تنبّها في الوسط،
     * ولأن الصفحة بتتم والنافذة ما بتتم معاه لحالها.
     */
    private PopupWindow categoryPopup;

    private final List<Product> products = new ArrayList<>();

    /**
     * المعرّفات المختارة، بترتيب نقرها. مجموعة لا قائمة لأن «اختيار الكل» قد تمرّ على
     * المنتج مرتين، والمجموعة تمنع التكرار وحدها.
     */
    private final Set<String> selectedIds = new LinkedHashSet<>();

    /** "" تعني كل المنتجات، وبقية القيم تصنيف بعينه وهو نفسه المحفوظ في المنتج */
    private String categoryFilter = "";

    private boolean percentType = true;

    /** الحفظ يستغرق ردّاً من السيرفر، والضغطة الثانية في أثنائه تنشر عرضاً مكرراً */
    private boolean submitting;

    /** معاينة البطاقة تتحرّك مع كل حرف، فلا تنتظر التاجر يضغط الحفظ ليرى شكلها */
    private final TextWatcher previewWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (binding != null) {
                renderPreview();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMerchantOffersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: لا متجر ولا منتجات ولا عرض يُبنى على لا شيء
            Toast.makeText(requireContext(), R.string.error_account_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        uid = account.getUid();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        startsAt = today.getTimeInMillis();
        endsAt = startsAt + (DEFAULT_SPAN_DAYS - 1) * DAY_MILLIS + DAY_MILLIS - 1;

        selectDiscountType(true);
        renderDates();
        renderPreview();

        binding.btnTypePercent.setOnClickListener(v -> selectDiscountType(true));
        binding.btnTypeFixed.setOnClickListener(v -> selectDiscountType(false));
        binding.etName.addTextChangedListener(previewWatcher);
        binding.etValue.addTextChangedListener(previewWatcher);

        binding.llCategory.setOnClickListener(v -> showCategoryPicker(v));
        binding.btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        binding.tvStartDate.setOnClickListener(v -> pickDate(true));
        binding.tvEndDate.setOnClickListener(v -> pickDate(false));
        binding.btnCreate.setOnClickListener(v -> create());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!uid.isEmpty()) {
            watchProducts();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissPicker();
        if (productsWatch != null) {
            productsWatch.remove();
            productsWatch = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissPicker();
        binding = null;
    }

    /** منتجات المتجر لشرائح «المنتجات المشمولة»: نفس المتابعة الحيّة التي في المنيو */
    private void watchProducts() {
        productsWatch = productRepository.watchForStore(uid,
                new ProductRepository.ListCallback() {
                    @Override
                    public void onProducts(@NonNull List<Product> loaded) {
                        runOnUi(() -> {
                            if (binding == null) {
                                return;
                            }
                            products.clear();
                            products.addAll(loaded);
                            pruneSelection();
                            buildProductPills();
                        });
                    }

                    @Override
                    public void onError() {
                        runOnUi(() -> {
                            if (binding != null) {
                                Toast.makeText(requireContext(), R.string.menu_load_failed,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
    }

    /** منتج حُذف من المنيو لا يبقى في عرض يُنشأ الآن: معرّفه لن يُطابق شيئاً للزبون */
    private void pruneSelection() {
        List<String> gone = new ArrayList<>();
        for (String id : selectedIds) {
            if (byId(id) == null) {
                gone.add(id);
            }
        }
        selectedIds.removeAll(gone);
    }

    @Nullable
    private Product byId(@NonNull String productId) {
        for (Product product : products) {
            if (productId.equals(product.getId())) {
                return product;
            }
        }
        return null;
    }

    /**
     * الشرائح تُبنى من منتجات التصنيف المختار وحده. كل شريحة تحمل رمز تصنيفها أمام
     * اسمها، لأن التصميم يجعل الرمز جزءاً من اسم الطبق في عين التاجر. وتنتهي
     * بتحديث نصّ «اختيار الكل»، فالبناء هو اللحظة التي تغيّر فيها النطاق.
     */
    private void buildProductPills() {
        binding.chipGroupProducts.removeAllViews();

        int shown = 0;
        for (Product product : products) {
            if (!categoryFilter.isEmpty() && !categoryFilter.equals(product.getCategory())) {
                continue;
            }
            binding.chipGroupProducts.addView(buildPill(product));
            shown++;
        }

        binding.tvNoProducts.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        binding.tvNoProducts.setText(products.isEmpty()
                ? R.string.offer_store_empty : R.string.offer_no_products);
        updateSelectAllLabel();
    }

    private View buildPill(@NonNull Product product) {
        ItemOfferProductBinding pill = ItemOfferProductBinding
                .inflate(LayoutInflater.from(requireContext()), binding.chipGroupProducts, false);

        String emoji = emojiForCategory(product.getCategory());
        pill.tvLabel.setText(emoji.isEmpty()
                ? product.getName() : emoji + " " + product.getName());

        paintPill(pill, selectedIds.contains(product.getId()));
        pill.getRoot().setOnClickListener(v -> {
            if (selectedIds.contains(product.getId())) {
                selectedIds.remove(product.getId());
            } else {
                selectedIds.add(product.getId());
            }
            paintPill(pill, selectedIds.contains(product.getId()));
            updateSelectAllLabel();
            renderPreview();
        });
        return pill.getRoot();
    }

    /** اللون والأيقونة قرار واحد: خوخى وصحّ للمختارة، ولافندر وعلامة زائد لغيرها */
    private void paintPill(@NonNull ItemOfferProductBinding pill, boolean selected) {
        pill.getRoot().setSelected(selected);
        pill.ivState.setImageResource(selected
                ? R.drawable.ic_check_circle : R.drawable.ic_add);
        pill.ivState.setImageTintList(ContextCompat.getColorStateList(requireContext(),
                selected ? R.color.primary_orange : R.color.secondary_navy));
    }

    /**
     * رمز التصنيف بالفهرس المطابق له في قائمة التصنيفات. الرمز للتصنيف لا للطبق:
     * «عصير برتقال» يمشي مع رمز المشروبات، ولو أردنا رمزاً لكل طبق لاحتاج حقلاً
     * يختاره التاجر في شاشة إضافة المنتج.
     */
    @NonNull
    private String emojiForCategory(@NonNull String category) {
        String[] categories = getResources().getStringArray(R.array.product_categories);
        String[] emojis = getResources().getStringArray(R.array.product_category_emojis);
        for (int index = 0; index < categories.length && index < emojis.length; index++) {
            if (categories[index].equals(category)) {
                return emojis[index];
            }
        }
        return "";
    }

    /**
     * لوحة التصنيفات النازلة: بطاقة بيضا فوق خانة الاختيار، لا قائمة النظام. السبب
     * أن PopupMenu ما بيسمح تتحكّم بخلفيّته ولا بخطّه، وألوان مدار ثابتة مش تابعة
     * لمزاج الجهاز. الأسطر تُبنى من منتجات التاجر وحدها: تصنيف بلا طبق ما ليه لازمة هنا.
     */
    private void showCategoryPicker(@NonNull View anchor) {
        if (categoryPopup != null && categoryPopup.isShowing()) {
            return;
        }

        LinearLayout panel = (LinearLayout) LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_category_picker, null, false);
        LinearLayout options = panel.findViewById(R.id.llOptions);

        options.addView(buildOption(options, getString(R.string.offer_category_all), ""));
        for (String category : getResources().getStringArray(R.array.product_categories)) {
            if (countOf(category) > 0) {
                options.addView(buildOption(options, category, category));
            }
        }

        // تغطّي الخانة نفسها كما يفعل السبينر: التاجر ما يحس إن الشاشة نزلت تحت يده
        int overlap = anchor.getHeight() + Math.round(dp(4));
        categoryPopup = new PopupWindow(panel, anchor.getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        categoryPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        categoryPopup.setElevation(dp(10));
        categoryPopup.showAsDropDown(anchor, 0, -overlap);
    }

    /** سطر واحد: المُفعّل منه له خلفية خوخية وعلامة صح برتقالية، وغيره نصّ عادي */
    private View buildOption(@NonNull ViewGroup parent, @NonNull String label,
                             @NonNull String value) {
        ItemCategoryOptionBinding option = ItemCategoryOptionBinding
                .inflate(LayoutInflater.from(requireContext()), parent, false);

        boolean chosen = value.equals(categoryFilter);
        option.getRoot().setSelected(chosen);
        option.tvOption.setText(label);
        option.ivMark.setVisibility(chosen ? View.VISIBLE : View.GONE);
        option.getRoot().setOnClickListener(v -> {
            categoryFilter = value;
            binding.tvCategory.setText(label);
            buildProductPills();
            dismissPicker();
        });
        return option.getRoot();
    }

    /** اللوحة بتعيش برّا الحاوية، فتنظيفها بيدنا لا بيد النظام */
    private void dismissPicker() {
        if (categoryPopup != null) {
            categoryPopup.dismiss();
            categoryPopup = null;
        }
    }

    private int countOf(@NonNull String category) {
        int count = 0;
        for (Product product : products) {
            if (category.equals(product.getCategory())) {
                count++;
            }
        }
        return count;
    }

    /**
     * معرّفات ما هو ظاهر في التصنيف المختار: هذا النطاق هو الذي يقرّر «اختيار الكل»
     * أمره، وهو الذي يُبنى عليه نصّ الزرار نفسه.
     */
    @NonNull
    private List<String> visibleIds() {
        List<String> visible = new ArrayList<>();
        for (Product product : products) {
            if (categoryFilter.isEmpty() || categoryFilter.equals(product.getCategory())) {
                visible.add(product.getId());
            }
        }
        return visible;
    }

    /**
     * نصّ الزرار يقول ماذا سينفّذ لا أمراً غامضاً: لمّا يتحدّد الكل يصير «إلغاء الكل»،
     * فما تحتاج اللوحة تنفتح عشان يعرف التاجر إن كانوا مختارين ولا لا.
     */
    private void updateSelectAllLabel() {
        List<String> visible = visibleIds();
        boolean allChosen = !visible.isEmpty() && selectedIds.containsAll(visible);
        binding.btnSelectAll.setText(allChosen
                ? R.string.offer_deselect_all : R.string.offer_select_all);
    }

    /** «اختيار الكل» على ما هو ظاهر لا على كل المنيو: التصنيف المختار هو ما يراه التاجر */
    private void toggleSelectAll() {
        List<String> visible = visibleIds();

        boolean allChosen = !visible.isEmpty() && selectedIds.containsAll(visible);
        for (String id : visible) {
            if (allChosen) {
                selectedIds.remove(id);
            } else {
                selectedIds.add(id);
            }
        }

        buildProductPills();
        renderPreview();
    }

    private void pickDate(boolean start) {
        Calendar seed = Calendar.getInstance();
        seed.setTimeInMillis(start ? startsAt : endsAt);

        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, day, 0, 0, 0);
            picked.set(Calendar.MILLISECOND, 0);

            if (start) {
                startsAt = picked.getTimeInMillis();
            } else {
                // آخر ثانية من اليوم، لا أولها: عرضٌ ينتهي في منتصف ليل آخر أيامه
                // يكون قد مات قبل أن يفتح التاجر محله صباح ذلك اليوم
                picked.add(Calendar.DAY_OF_YEAR, 1);
                endsAt = picked.getTimeInMillis() - 1;
            }
            renderDates();
            renderPreview();
        }, seed.get(Calendar.YEAR), seed.get(Calendar.MONTH), seed.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void renderDates() {
        binding.tvStartDate.setText(dateFormat.format(new Date(startsAt)));
        binding.tvEndDate.setText(dateFormat.format(new Date(endsAt)));
    }

    /** قلب نوع الخصم: البطاقة البيضاء تنتقل، والوحدة في خانة القيمة تتبدّل معها */
    private void selectDiscountType(boolean percent) {
        percentType = percent;
        binding.btnTypePercent.setSelected(percent);
        binding.btnTypeFixed.setSelected(!percent);
        binding.btnTypePercent.setElevation(dp(percent ? 2 : 0));
        binding.btnTypeFixed.setElevation(dp(percent ? 0 : 2));
        binding.tvUnit.setText(percent ? R.string.percent_sign : R.string.currency_symbol);
        renderPreview();
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /**
     * بطاقة المعاينة: اسم العرض وشارة خصمه وحالته. الاسم الفاضي يُعرض كما سيُرفض
     * بالاسم نفسه لا بكلمة «عرض»، فالتاجر يرى ما ينقصه في المكان الذي يراه فيه الزبون.
     */
    private void renderPreview() {
        String name = binding.etName.getText().toString().trim();
        binding.tvPreviewName.setText(name.isEmpty()
                ? getString(R.string.hint_offer_name) : name);

        double value = readValue();
        String amount = value <= 0
                ? "" : percentType
                ? (trimNumber(value) + getString(R.string.percent_sign))
                : (getString(R.string.currency_symbol) + trimNumber(value));
        binding.tvPreviewBadge.setText(amount.isEmpty()
                ? getString(R.string.offer_preview_discount, "…")
                : getString(R.string.offer_preview_discount, amount));

        long now = System.currentTimeMillis();
        int label;
        int color;
        if (now > endsAt) {
            label = R.string.offer_ended;
            color = R.color.text_secondary;
        } else if (now < startsAt) {
            label = R.string.offer_scheduled;
            color = R.color.secondary_navy;
        } else {
            label = R.string.offer_live;
            color = R.color.primary_orange;
        }
        binding.tvLive.setText(label);
        binding.tvLive.setTextColor(ContextCompat.getColor(requireContext(), color));
    }

    private double readValue() {
        String raw = binding.etValue.getText().toString().trim().replace(',', '.');
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    /** عدد بلا كسور زائدة: ٢٠٫٠ تُقرأ «20»، و٧٫٥ تبقى على حالها */
    private String trimNumber(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.format(Locale.US, "%.2f", value);
    }

    /**
     * التحقق قبل الكتابة، وكل فشل برسالة تقول ماذا ينقص لا أن الإدخال خاطئ فقط.
     * نسبة فوق التسعين مرفوضة: التاجر الذي يكتب ١٠٠٪ غالباً يقصد شيئاً غير ما كتبه.
     */
    private void create() {
        if (submitting) {
            return;
        }

        String name = binding.etName.getText().toString().trim();
        if (name.length() < 3) {
            fail(R.string.error_offer_name);
            return;
        }

        double value = readValue();
        if (value < Offer.MIN_VALUE) {
            fail(R.string.error_offer_value);
            return;
        }
        if (percentType && value > Offer.MAX_PERCENT) {
            fail(R.string.error_offer_value_high);
            return;
        }

        if (selectedIds.isEmpty()) {
            fail(R.string.error_offer_products);
            return;
        }
        if (endsAt < startsAt) {
            fail(R.string.error_offer_dates);
            return;
        }

        Offer offer = new Offer(uid, name,
                percentType ? Offer.TYPE_PERCENT : Offer.TYPE_FIXED, value);
        offer.setProductIds(new ArrayList<>(selectedIds));
        offer.setStartsAt(startsAt);
        offer.setEndsAt(endsAt);

        submitting = true;
        binding.btnCreate.setEnabled(false);
        offerRepository.create(offer, new OfferRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                runOnUi(() -> {
                    doneSubmitting();
                    if (binding == null) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.msg_offer_created,
                            Toast.LENGTH_SHORT).show();
                    resetForm();
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> {
                    doneSubmitting();
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.error_offer_save_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void fail(int messageRes) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show();
    }

    private void doneSubmitting() {
        submitting = false;
        if (binding != null) {
            binding.btnCreate.setEnabled(true);
        }
    }

    /** النموذج يعود فارغاً بعد العرض: التاجر الذي يريد عرضاً ثانياً لا يعدّل الأول سرّاً */
    private void resetForm() {
        binding.etName.setText("");
        binding.etValue.setText("");
        selectedIds.clear();
        categoryFilter = "";
        binding.tvCategory.setText(R.string.offer_category_all);
        buildProductPills();
        renderPreview();
    }

    /**
     * تنفيذ على خيط الواجهة إن كانت الصفحة ما زالت متصلة بنشاطها: اللقطة قد تصل
     * والتاجر قد قلب الشاشة أو انتقل لتبويب آخر.
     */
    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }
}
