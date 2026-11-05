package com.taqat.madar.ui.customer.menu;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.taqat.madar.R;
import com.taqat.madar.data.local.CustomerCart;
import com.taqat.madar.data.model.Product;
import com.taqat.madar.data.model.ProductOptionGroup;
import com.taqat.madar.databinding.ItemSheetOptionGroupBinding;
import com.taqat.madar.databinding.ItemSheetOptionRowBinding;
import com.taqat.madar.databinding.ItemSheetOptionTileBinding;
import com.taqat.madar.databinding.SheetProductDetailsBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * نافذة تفاصيل الوجبة: وجه الطبق كما يصنعه الزبون — اختياراته إن كانت، وملاحظته،
 * وعدد الوحدات منه.
 *
 * كل ما فيها يُكتب في السلة لحظة لمسه، بلا زر إضافة وبلا زر إلغاء: الرسمة ما فيها
 * إلغاء، والنافذة تُغلَق من ✕ وما قاله الزبون فيها يبقى قوله. لذلك بقي زر «تم» وحده
 * يقول سعر الوجه الذي صنعه — لأن رقماً بلا سبب يترك الزبون يسأل: هل حسبت لي المضافات
 * ولا لا.
 *
 * السطر الذي تفتحه النافذة هو آخر سطر لهذا الصنف في السلة، لا سطراً جديداً بجانبه:
 * الزبون يعدّل الوجه الذي تركه آخر مرة، وتغيير الاختيار ينقله لا يضاعفه. ولو كان
 * للوجه الجديد سطر موجود اتّحدا سطراً واحداً — لأن صنفًا بوجه واحد لا يكون سطرين.
 *
 * صنف بلا اختيارات تُترك النافذة للكمية والملاحظة وحدهما: قسم الاختيارات يبقى مخفياً
 * كما وُضع في الرسم، فلا عنوان فاضي ولا صف بلا سبب.
 */
public class ProductDetailsSheet extends BottomSheetDialog {

    /** الشاشة التي عرضت البطاقة تُحدَّث بعد كل كتابة: الشريط والبطاقة يقرآن السلة نفسها */
    public interface OnCartChanged {
        void onCartChanged();
    }

    /**
     * خيار معروض: بلاطة أو صف، والاسم هو ما يُختار لا موضعه في القائمة. الشكلان يُصبَغان
     * بطريقة مختلفة، فالسؤال الواحد بدائرة والسؤال الذي يُجمع بمربّع.
     */
    private abstract static class Control {

        final String name;
        final View view;

        Control(@NonNull String name, @NonNull View view) {
            this.name = name;
            this.view = view;
        }

        abstract void paint(boolean selected);
    }

    /** بلاطة مجموعة لا تقبل إلا وجهاً واحداً */
    private static final class TileControl extends Control {

        private final ImageView marker;

        TileControl(@NonNull ItemSheetOptionTileBinding tile) {
            super(tile.tvName.getText().toString(), tile.getRoot());
            this.marker = tile.ivMarker;
        }

        @Override
        void paint(boolean selected) {
            view.setSelected(selected);
            marker.setImageResource(selected ? R.drawable.ic_radio_on : R.drawable.ic_radio_off);
        }
    }

    /** صف مجموعة يقبل الجمع: المربّع والصحّ داخله حالة واحدة */
    private static final class RowControl extends Control {

        private final View box;
        private final View check;

        RowControl(@NonNull ItemSheetOptionRowBinding row) {
            super(row.tvName.getText().toString(), row.getRoot());
            this.box = row.viewCheckbox;
            this.check = row.ivCheck;
        }

        @Override
        void paint(boolean selected) {
            view.setSelected(selected);
            box.setBackgroundResource(selected
                    ? R.drawable.bg_checkbox_on : R.drawable.bg_checkbox_off);
            check.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    private final SheetProductDetailsBinding sheet;
    private final Product product;
    private final CustomerCart cart;
    private final OnCartChanged listener;

    /** مجموعة إلى ما يُعرض فيها من خيارات */
    private final List<List<Control>> controls = new ArrayList<>();

    /** مجموعة إلى الأسماء المختارة فيها، بترتيب المجموعات كما كتبه التاجر */
    private final List<List<String>> chosen = new ArrayList<>();

    /** ما كان مختاراً في السطر الذي فُتحت النافذة عليه */
    private final List<String> openOptions = new ArrayList<>();

    /** السطر الذي تملكه النافذة الآن: كل كتابة تنقله ولا تترك منه نسخة */
    private String ownedKey = "";

    private int qty;
    private String note = "";

    public ProductDetailsSheet(@NonNull Context context, @NonNull Product product,
                               @NonNull CustomerCart cart, @NonNull OnCartChanged listener) {
        super(context);
        this.product = product;
        this.cart = cart;
        this.listener = listener;

        sheet = SheetProductDetailsBinding.inflate(LayoutInflater.from(context));
        setContentView(sheet.getRoot());

        // الاستدارة على الحاوية اللي جوّاها المحتوى لا على المحتوى نفسه: هي صاحبة
        // الحواف، وهي نفس الطريقة التي تُبنى بها بقية أوراق التطبيق
        setOnShowListener(shown -> {
            View frame = (View) sheet.getRoot().getParent();
            if (frame == null) {
                return;
            }
            frame.setBackgroundResource(R.drawable.bg_sheet_top);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(frame);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        if (getWindow() != null) {
            // الكيبورد يرفع النافذة فوقه فلا يغطّي خانة الملاحظة
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        CustomerCart.Line open = cart.lineOf(product.getId());
        ownedKey = open == null ? CustomerCart.plainKey(product.getId()) : open.getKey();
        qty = open == null ? 0 : open.getQuantity();
        note = open == null ? "" : open.getNote();
        if (open != null) {
            openOptions.addAll(open.getOptions());
        }

        renderProduct();
        buildOptionGroups();
        wireNotes();
        wireStepper();

        sheet.btnClose.setOnClickListener(v -> dismiss());
        sheet.btnDone.setOnClickListener(v -> dismiss());

        paintOptions();
        paintStepper();
        paintDone();
    }

    /** رأس الصنف: اسمه ووصفه وسعره وصورته، وكلها من وثيقة products نفسها */
    private void renderProduct() {
        sheet.tvName.setText(product.getName());
        sheet.tvPrice.setText(money(product.getPrice()));
        sheet.tvDescription.setText(product.getDescription());
        sheet.tvDescription.setVisibility(
                product.getDescription().isEmpty() ? View.GONE : View.VISIBLE);

        if (product.hasPhoto()) {
            sheet.ivPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            sheet.ivPhoto.setImageTintList(null);
            Glide.with(getContext())
                    .load(product.getPhotoUrl())
                    .placeholder(R.drawable.ic_restaurant)
                    .into(sheet.ivPhoto);
            return;
        }

        // بلا صورة: أيقونة المطعم كما في البطاقة، داخل المربّع لا مقصوصة عليه
        sheet.ivPhoto.setImageResource(R.drawable.ic_restaurant);
        sheet.ivPhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sheet.ivPhoto.setImageTintList(
                ContextCompat.getColorStateList(getContext(), R.color.secondary_navy));
    }

    /**
     * مجموعاتها من product.options: الواحدة بلاطات بصفوف، والمتعددة صفوف فوق بعضها.
     * والنافذة لا تُبدل شكلاً بحالة لا تملكها — فالتلوين كله من قائمة المختارات.
     */
    private void buildOptionGroups() {
        if (!product.hasOptions()) {
            return;
        }
        sheet.layoutOptions.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (ProductOptionGroup group : product.getOptions()) {
            ItemSheetOptionGroupBinding head =
                    ItemSheetOptionGroupBinding.inflate(inflater, sheet.layoutOptions, false);

            head.tvGroupTitle.setText(group.getTitle());
            head.tvGroupHint.setText(group.isMultiple()
                    ? R.string.cust_sheet_optional : R.string.cust_sheet_pick_one);

            List<Control> groupControls = new ArrayList<>();
            List<String> groupChosen = new ArrayList<>();
            controls.add(groupControls);
            chosen.add(groupChosen);

            if (group.isMultiple()) {
                for (ProductOptionGroup.Item item : group.getItems()) {
                    ItemSheetOptionRowBinding row =
                            ItemSheetOptionRowBinding.inflate(inflater, head.groupMulti, false);
                    fillRow(row, item);
                    head.groupMulti.addView(row.getRoot());
                    groupControls.add(new RowControl(row));
                }
            } else {
                LinearLayout row = null;
                for (int index = 0; index < group.getItems().size(); index++) {
                    if (index % 2 == 0) {
                        row = addTileRow(head.groupSingle, index > 0);
                    }
                    ProductOptionGroup.Item item = group.getItems().get(index);
                    ItemSheetOptionTileBinding tile =
                            ItemSheetOptionTileBinding.inflate(inflater, row, false);
                    fillTile(tile, item);
                    tile.getRoot().setLayoutParams(tileParams(index % 2 == 0));
                    row.addView(tile.getRoot());
                    groupControls.add(new TileControl(tile));
                }
            }

            // المختار يُستعاد بالاسم وحده: الاسم هو مفتاح السطر في السلة والطلب
            for (int index = 0; index < group.getItems().size(); index++) {
                String name = group.getItems().get(index).getName();
                if (openOptions.contains(name)
                        && (group.isMultiple() || groupChosen.isEmpty())) {
                    groupChosen.add(name);
                }
            }

            wireControls(groupControls, groupChosen, group.isMultiple());
            sheet.layoutOptions.addView(head.getRoot());
        }
    }

    /** بلاطة: الاسم، والسعر زيادةً لا أصلاً — فخبز الصاج لا يُباع وحده */
    private void fillTile(@NonNull ItemSheetOptionTileBinding tile,
                          @NonNull ProductOptionGroup.Item item) {
        tile.tvName.setText(item.getName());
        bindPrice(tile.tvPrice, item);
    }

    private void fillRow(@NonNull ItemSheetOptionRowBinding row,
                         @NonNull ProductOptionGroup.Item item) {
        row.tvName.setText(item.getName());
        bindPrice(row.tvPrice, item);
    }

    /** صفراً لا يعني «مجاني» بل أن السؤال عن الزيادة لا يُسأل: فلا رقم على البلاطة ولا على الصف */
    private void bindPrice(@NonNull TextView label,
                           @NonNull ProductOptionGroup.Item item) {
        if (item.getPrice() <= 0) {
            label.setVisibility(View.GONE);
            return;
        }
        label.setText(getContext().getString(R.string.cust_sheet_option_price,
                money(item.getPrice())));
    }

    /** صفوف البلاطتين: كما في الرسمة، وجهان جنب بعضهما في كل صف */
    @NonNull
    private LinearLayout addTileRow(@NonNull LinearLayout parent, boolean spaced) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (spaced) {
            params.topMargin = dp(8f);
        }
        row.setLayoutParams(params);
        parent.addView(row);
        return row;
    }

    /** البلاطة تأخذ نصف الصف مع وزن، والفرق بينهما على يسار اليمنى كما في الرسمة */
    @NonNull
    private LinearLayout.LayoutParams tileParams(boolean leading) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(leading ? dp(8f) : 0);
        return params;
    }

    private void wireControls(@NonNull List<Control> groupControls,
                              @NonNull List<String> groupChosen, boolean multiple) {
        for (Control control : groupControls) {
            control.view.setOnClickListener(v -> {
                if (multiple) {
                    if (!groupChosen.remove(control.name)) {
                        groupChosen.add(control.name);
                    }
                } else {
                    // وجه واحد لا غير: تُفرغ المجموعة قبل الاختيار، وإلا صار الطبق بخبزين.
                    // والضغط على المختارة يلغي السؤال كله، فليس عندنا إجبار
                    boolean was = groupChosen.contains(control.name);
                    groupChosen.clear();
                    if (!was) {
                        groupChosen.add(control.name);
                    }
                }
                apply();
            });
        }
    }

    private void wireNotes() {
        sheet.etNotes.setText(note);
        sheet.etNotes.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String typed = s == null ? "" : s.toString();
                if (typed.equals(note)) {
                    return;
                }
                note = typed;
                apply();
            }
        });
    }

    private void wireStepper() {
        sheet.stepper.btnPlus.setOnClickListener(v -> {
            qty++;
            apply();
        });
        sheet.stepper.btnMinus.setOnClickListener(v -> {
            if (qty > 0) {
                qty--;
                apply();
            }
        });
    }

    /**
     * الكتابة في السلة: ما في اليد الآن هو السطر الذي كان مفتوحاً، بملاحظته وأوجهه
     * وعددته. والنافذة لا تُضيف وحدة على ما هو محفوظ — فعدّادها عدد السطر لا زيادته.
     */
    private void apply() {
        List<String> flat = flatChosen();
        cart.writeVariant(ownedKey, product, flat, note.trim(), qty);
        ownedKey = CustomerCart.keyOf(product.getId(), flat);

        paintOptions();
        paintStepper();
        paintDone();
        listener.onCartChanged();
    }

    private void paintOptions() {
        for (int index = 0; index < controls.size(); index++) {
            List<String> groupChosen = chosen.get(index);
            for (Control control : controls.get(index)) {
                control.paint(groupChosen.contains(control.name));
            }
        }
    }

    /** الصفر حالة بلا ناقص ولا عدد: تبقى حبّة الزائد وحدها كما في البطاقة */
    private void paintStepper() {
        boolean empty = qty == 0;
        sheet.stepper.btnMinus.setVisibility(empty ? View.GONE : View.VISIBLE);
        sheet.stepper.tvQty.setVisibility(empty ? View.GONE : View.VISIBLE);
        sheet.stepper.tvQty.setText(String.valueOf(qty));
        sheet.stepper.getRoot().setBackgroundResource(
                empty ? 0 : R.drawable.bg_stepper_bar);
    }

    /** الزر يقول سعر الوجه بلا العدد: العدد على العدّاد جنبه، والسطر بلا كمية سعره وحده */
    private void paintDone() {
        sheet.btnDone.setText(getContext().getString(R.string.cust_sheet_done_total,
                money(product.priceWith(flatChosen()))));
    }

    @NonNull
    private List<String> flatChosen() {
        List<String> flat = new ArrayList<>();
        for (List<String> groupChosen : chosen) {
            flat.addAll(groupChosen);
        }
        return flat;
    }

    private String money(double value) {
        return getContext().getString(R.string.currency_symbol)
                + String.format(Locale.US, "%.2f", value);
    }

    private int dp(float value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
