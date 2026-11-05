package com.taqat.madar.ui.merchant.menu;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.R;
import com.taqat.madar.data.local.MerchantNotificationStore;
import com.taqat.madar.data.model.Product;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.ProductRepository;
import com.taqat.madar.databinding.FragmentMerchantMenuBinding;
import com.taqat.madar.databinding.ItemProductCardBinding;
import com.taqat.madar.ui.merchant.notifications.MerchantNotificationsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * تبويب المنيو: كل منتجات متجر التاجر في لائحة واحدة.
 *
 * اللائحة متابعة لمجموعة products مباشرة، فمنتج يُنشر من شاشة الإضافة يظهر هنا بلا
 * ما يعيد التاجر تحميل الصفحة. والشرائح تُبنى من المنتجات نفسها لا من كل التصنيفات:
 * شريحة «حلويات (0)» لا تصفية بها، فقط تزحم الرأس.
 *
 * التوفّر وحده ما يتحكّم بالمنتج: السويتش والشارة قرار واحد، لأن مدار لا تعرف صنفاً
 * مخفياً عن الزبون وغير متوفر في وقت واحد — النتيجة واحدة أن لا طلب يصل عليه.
 */
public class MerchantMenuFragment extends Fragment {

    private final AuthRepository authRepository = new AuthRepository();
    private final ProductRepository productRepository = new ProductRepository();

    private FragmentMerchantMenuBinding binding;
    private String uid = "";
    private ListenerRegistration productsWatch;

    /** لائحة الإشعارات على الجهاز: منها يقرأ الجرس هل عليه ما لم يُقرأ بعد */
    private MerchantNotificationStore notifications;

    private final List<Product> allProducts = new ArrayList<>();

    /**
     * البطاقات المعروضة بمعرّف منتجها. اللقطة الجديدة تُعيد تعبئة البطاقة القائمة
     * لا تبني واحدة مكانها، فالحلقة السابقة كانت تهدم السويتش الذي لمسته يد التاجر
     * للتو فينقلب شكله أمامه، وتُنزل صورة المنتج من جديد بلا سبب.
     */
    private final Map<String, ItemProductCardBinding> cards = new HashMap<>();

    /** عدّادات الشرائح من اللقطة السابقة: ما تغيّر منها شيء ما يُعاد بناؤها */
    private String filterSignature = "";

    /** "" تعني الكل، وبقية القيم اسم تصنيف بعينه وهو نفسه المحفوظ في الوثيقة */
    private String selectedFilter = "";

    /** شاشة الإضافة تُفتح وحدها أول ما يكتشف التاجر أن منيوه فاضي، لا مع كل لقطة */
    private boolean openedAddForEmpty;

    /** يمنع السويتش أن يكتب قراراً جديداً وهو يُرجع حالته القديمة بعد فشل الحفظ */
    private boolean rebinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMerchantMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: التاجر خارج التطبيق أصلاً، ولا معنى لشاشة منيو بلا متجر
            Toast.makeText(requireContext(), R.string.error_account_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        uid = account.getUid();

        binding.btnAddProduct.setOnClickListener(v -> openAddProduct());

        notifications = new MerchantNotificationStore(requireContext(), uid);
        binding.ivNotifications.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), MerchantNotificationsActivity.class)));
        refreshUnreadBell();

        // اختيار التصفية يُسمع مرة واحدة: إعادة بناء الشرائح مع كل لقطة ما بتغيّر المستمع
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            Chip chosen = group.findViewById(checkedIds.get(0));
            selectedFilter = chosen == null || chosen.getTag() == null
                    ? "" : chosen.getTag().toString();
            renderList();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!uid.isEmpty()) {
            watchProducts();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // قد يكون قرأ سطوراً من شاشة الإشعارات وعاد، فالجرس يتبع ما بقي منها
        refreshUnreadBell();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (productsWatch != null) {
            productsWatch.remove();
            productsWatch = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        // البطاقات كانت متصلة بشجرة views محترقة، والإمساك بها يتركها في الذاكرة
        cards.clear();
        // وبصمة الشرائح كمان تُمسح: الحاوية الجديدة فاضية، ولو بقيت البصمة على حالها
        // لاعتبر بناء الشرائح أن شيئاً لم يتغيّر، فلا تُركَّب شريحة أبداً
        filterSignature = "";
    }

    /**
     * متابعة منتجات المتجر لحظة بلحظة. الاستماع يُزال في onStop بالكائن المُرجَع،
     * لئلا يبقى يعمل والصفحة مخفية خلف تبويب آخر.
     */
    private void watchProducts() {
        productsWatch = productRepository.watchForStore(uid,
                new ProductRepository.ListCallback() {
                    @Override
                    public void onProducts(@NonNull List<Product> products) {
                        runOnUi(() -> {
                            if (binding == null) {
                                return;
                            }
                            allProducts.clear();
                            allProducts.addAll(products);
                            if (!products.isEmpty()) {
                                openedAddForEmpty = false;
                            }
                            buildFilters();
                            renderList();
                            openAddWhenEmpty(products);
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

    /** أول مرة يفتح المنيو وهو فاضي تذهب به إلى الإضافة مباشرة، فليس هناك ما يعرضه */
    private void openAddWhenEmpty(@NonNull List<Product> products) {
        if (!products.isEmpty() || openedAddForEmpty) {
            return;
        }
        openedAddForEmpty = true;
        openAddProduct();
    }

    /**
     * شريحة «الكل» ثم كل تصنيف فيه منتج واحد على الأقل. الأرقام تُحسب من المنتجات
     * المعروضة، فتغيير منتج تصنيفه يقلّب العدّادات في اللقطة التالية.
     *
     * الشرائح تُبنى من الصفر مع كل لقطة لأن عددها يتغيّر: تصنيف كان فارغاً يصير له
     * منتج، والمختار منها يُعاد ضبطه بعد البناء وإلا سقطت تصفية التاجر.
     */
    private void buildFilters() {
        String signature = signatureOfFilters();
        if (signature.equals(filterSignature)) {
            // العدّادات كما هي: بناء الشرائح من جديد كان يُسقط المختارة ويُعيد
            // تركيبها، فتتوه الضغطة ويُستدعى عرض اللائحة مرة إضافية
            return;
        }
        filterSignature = signature;

        binding.chipGroupFilter.removeAllViews();

        Chip all = addFilterChip("", getString(R.string.menu_filter_all, allProducts.size()));

        String[] categories = getResources().getStringArray(R.array.product_categories);
        Chip chosen = all;
        boolean filterStillExists = selectedFilter.isEmpty();
        for (String category : categories) {
            int count = countOf(category);
            if (count == 0) {
                continue;
            }
            Chip chip = addFilterChip(category,
                    getString(R.string.menu_filter_count, category, count));
            if (category.equals(selectedFilter)) {
                chosen = chip;
                filterStillExists = true;
            }
        }

        if (!filterStillExists) {
            // التصنيف ما عاد فيه منتج: نرجع للكل بدل شاشة فارغة بلا سبب ظاهر
            selectedFilter = "";
        }

        binding.chipGroupFilter.check(chosen.getId());
    }

    private Chip addFilterChip(@NonNull String category, @NonNull String label) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_chip, binding.chipGroupFilter, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(category);
        binding.chipGroupFilter.addView(chip);
        return chip;
    }

    /** بصمة عدّادات الشرائح، تُقارَن بين اللقطات فلا تُبنى الشرائح إلا عند تغيّر عدد */
    @NonNull
    private String signatureOfFilters() {
        StringBuilder signature = new StringBuilder().append(allProducts.size());
        for (String category : getResources().getStringArray(R.array.product_categories)) {
            signature.append('|').append(category).append(':').append(countOf(category));
        }
        return signature.toString();
    }

    private int countOf(@NonNull String category) {
        int count = 0;
        for (Product product : allProducts) {
            if (category.equals(product.getCategory())) {
                count++;
            }
        }
        return count;
    }

    /**
     * اللائحة تُوائم لا تُبنى من جديد: البطاقة القائمة يُعاد تعبئة نصّها وتُنقل لمكانها
     * إن تغيّر الترتيب، وتُزال إن حذف منتجها أو أخرجتها التصفية. هذا يترك السويتش
     * الذي بيده مكانه، ويوفر تنزيل كل صورة مع كل لقطة.
     *
     * إعادة الوضع مشروطة: Android يرفض addView لولد له أب أصلاً، ولو كان الأب هو
     * نفس الحاوية. فاللقطة التانية كانت بتحط البطاقة اللي هي في مكانها أصلو،
     * فيطير IllegalStateException. الآن كل بطاقة في موضعها الصحيح بتتترك كما هي،
     * وما بنلمسهاش إلا عند تغيّر ترتيبها أو تبديل التصفية.
     */
    private void renderList() {
        List<Product> visible = new ArrayList<>();
        Set<String> visibleIds = new HashSet<>();
        for (Product product : allProducts) {
            if (selectedFilter.isEmpty() || selectedFilter.equals(product.getCategory())) {
                visible.add(product);
                visibleIds.add(product.getId());
            }
        }

        for (String id : new ArrayList<>(cards.keySet())) {
            if (!visibleIds.contains(id)) {
                binding.listProducts.removeView(cards.remove(id).getRoot());
            }
        }

        for (int index = 0; index < visible.size(); index++) {
            Product product = visible.get(index);
            String id = product.getId();

            ItemProductCardBinding card = cards.get(id);
            if (card == null) {
                card = ItemProductCardBinding.inflate(LayoutInflater.from(requireContext()),
                        binding.listProducts, false);
                bindCardOnce(card, id);
                cards.put(id, card);
            }

            fillCard(card, product);

            View root = card.getRoot();
            int placedAt = binding.listProducts.indexOfChild(root);
            if (placedAt == index) {
                continue;
            }
            if (placedAt >= 0) {
                binding.listProducts.removeViewAt(placedAt);
            } else if (root.getParent() != null) {
                // بطاقة لسه متصلة بحاوية قديمة اتحرقت: نفصلها بإيدنا قبل الإضافة
                ((ViewGroup) root.getParent()).removeView(root);
            }
            binding.listProducts.addView(root,
                    Math.min(index, binding.listProducts.getChildCount()));
        }

        binding.tvEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        binding.tvEmpty.setText(visible.isEmpty() && !allProducts.isEmpty()
                ? R.string.menu_no_matches : R.string.menu_empty);
    }

    /**
     * المستمع تُركّب مرة واحدة لعمر البطاقة، ووقت الضغط يُقرأ المنتج من اللائحة
     * الحالية بمعرّفه: المعرّف ثابت، أما نسخة المنتج الملتقطة وقت البناء فتقادم.
     */
    private void bindCardOnce(@NonNull ItemProductCardBinding card, @NonNull String productId) {
        card.switchAvailable.setOnCheckedChangeListener((button, checked) -> {
            if (rebinding) {
                return;
            }
            Product current = byId(productId);
            if (current != null) {
                setAvailable(card, current, checked);
            }
        });

        card.btnEdit.setOnClickListener(v -> {
            Product current = byId(productId);
            if (current != null) {
                openEditProduct(current);
            }
        });
    }

    /** كل ما على الوجه: الاسم والتصنيف والسعر والصورة والشارة وحالة السويتش */
    private void fillCard(@NonNull ItemProductCardBinding card, @NonNull Product product) {
        card.tvName.setText(product.getName());
        card.tvCategory.setText(product.getCategory());
        card.tvPrice.setText(getString(R.string.currency_symbol) + formatPrice(product.getPrice()));

        showPhoto(card, product);
        showAvailability(card, product);

        // الضبط بحارسه: لولا rebinding لقرأ السويتش ضبط الشاشة قراراً جديداً من التاجر
        rebinding = true;
        card.switchAvailable.setChecked(product.isAvailable());
        rebinding = false;
    }

    /** المنتج الحالي بمعرّفه، أو null إن حذف وهو معروض */
    @Nullable
    private Product byId(@NonNull String productId) {
        for (Product product : allProducts) {
            if (productId.equals(product.getId())) {
                return product;
            }
        }
        return null;
    }

    /** منتج بلا صورة مقبول: أيقونة الطبق مكانها، فالاسم والسعر يكفيان للطلب */
    private void showPhoto(@NonNull ItemProductCardBinding card, @NonNull Product product) {
        // آخر رابط حُمّل محفوظ على الوسم: البطاقة واحدة لمنتج واحد، فما يُعاد
        // طلب الصورة مع كل لقطة وإلا يومض وجه البطاقة في عين التاجر
        Object shown = card.ivPhoto.getTag();

        if (!product.hasPhoto()) {
            if ("".equals(shown)) {
                return;
            }
            card.ivPhoto.setTag("");
            card.ivPhoto.setImageResource(R.drawable.ic_restaurant);
            card.ivPhoto.setImageTintList(ContextCompat.getColorStateList(requireContext(),
                    R.color.text_secondary));
            return;
        }
        if (product.getPhotoUrl().equals(shown)) {
            return;
        }
        card.ivPhoto.setTag(product.getPhotoUrl());
        Glide.with(this)
                .load(product.getPhotoUrl())
                .placeholder(R.drawable.ic_restaurant)
                .into(card.ivPhoto);
        card.ivPhoto.setImageTintList(null);
    }

    /** الشارة تتبع التوفر، فحالتها تُقرأ من قرار واحد مع السويتش */
    private void showAvailability(@NonNull ItemProductCardBinding card, @NonNull Product product) {
        card.tvBadge.setText(product.isAvailable()
                ? R.string.product_available : R.string.product_unavailable);
        card.tvBadge.setBackgroundResource(product.isAvailable()
                ? R.drawable.bg_badge_success : R.drawable.bg_badge_danger);
        card.tvBadge.setTextColor(ContextCompat.getColor(requireContext(), product.isAvailable()
                ? R.color.success_green : R.color.danger_red));
    }

    /**
     * الجرس مفرّغ حين لا شيء ينتظر التاجر، ومصمتٌ حين في انتظاره إشعار لم يقرأه.
     *
     * العدد من لائحة الجهاز التي يكتبها مولّد الإشعارات في واجهة التاجر، فالبطاقة
     * المقروءة في شاشة الإشعارات تُنقصه وهي تُقرأ، والعودة من هناك تحدّث الجرس.
     */
    private void refreshUnreadBell() {
        if (notifications == null) {
            return;
        }
        showUnreadNotifications(notifications.unreadCount() > 0);
    }

    private void showUnreadNotifications(boolean hasUnread) {
        binding.ivNotifications.setImageResource(hasUnread
                ? R.drawable.ic_notifications : R.drawable.ic_notifications_none);
    }

    /**
     * تغيير التوفر من السويتش: التحديث جزئي في الوثيقة، والفشل يُرجع
     * السويتش والشارة إلى ما كانا عليه، فلا يبقى التاجر يظن أن صنفه مخفي وهو ظاهر.
     */
    private void setAvailable(@NonNull ItemProductCardBinding card, @NonNull Product product,
                              boolean available) {
        productRepository.setAvailable(product.getId(), available,
                new ProductRepository.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        product.setAvailable(available);
                    }

                    @Override
                    public void onError() {
                        runOnUi(() -> {
                            if (binding == null) {
                                return;
                            }
                            // الإرجاع بلا مستمع: لولا الحارس لأشعلت كل محاولة فاشلة
                            // كتابة جديدة مكانها، فتتوالى المحاولات بلا توقف
                            rebinding = true;
                            card.switchAvailable.setChecked(product.isAvailable());
                            rebinding = false;
                            showAvailability(card, product);
                            Toast.makeText(requireContext(), R.string.menu_availability_failed,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private String formatPrice(double price) {
        return String.format(Locale.US, "%.2f", price);
    }

    private void openAddProduct() {
        startActivity(new Intent(requireContext(), AddProductActivity.class));
    }

    private void openEditProduct(@NonNull Product product) {
        startActivity(AddProductActivity.editIntent(requireContext(), product));
    }

    /**
     * تنفيذ على خيط الواجهة إن كانت الصفحة ما زالت متصلة بنشاطها: اللقطة قد تصل
     * والتاجر قد قلب الشاشة أو أغلق التطبيق.
     */
    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }
}
