package com.taqat.madar.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.taqat.madar.data.model.Product;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * سلة الزبون كلها في كل المحلات، محفوظة على الجهاز لا في السيرفر.
 *
 * السلة واحدة للمول كله لا لكل متجر سلة: الزبون يجمع من مطعم ندى ومن مطعم أحمد ومن
 * الصيدلية، ويدفع مرة واحدة في الآخر، فسطر السلة يحمل معرّف محله مع نفسه لأن الطلب
 * الذي سيُنشأ لكل متجر على حدة — الدفع واحد والطلبات متعددة.
 *
 * السلة نيّة لم تكتمل بعد: لا عنوان تسليم محفوظ ولا طريقة دفع ولا طلب يُنشأ إلا عند
 * الدفع، فكتابة نصف طلب في Firestore كانت ستملأ قاعدة البيانات سطوراً تُرمى أكثر
 * مما تُكمل، وتُلزم قواعد الأمان بسماح كتابة ما لا يملكه أحد.
 *
 * المفتاح معرّف الزبون وحده: زبونان على الجهاز نفسه لا يتبادلان أصنافاً، وحين تُبنى
 * شاشة الدفع ويُنشأ الطلب الحقيقي تُفرَّغ السلة من هناك، فتكون هي صاحب القرار لا هنا.
 *
 * سطر السلة نسخة من الصنف لا مرجع له: الاسم والسعر يُحفظان وقت الإضافة، فيبقى ما
 * يراه الزبون هو ما سيدفعه، ولو غيّر التاجر سعر طبقه والسلة ما زالت مفتوحة. هذا هو
 * العقد المتّبع في أصناف الطلب نفسها، فالسلة والطلب يقرآن بالمنطق الواحد.
 *
 * الكتابة في السلة كلها تمرّ بـ writeVariant، وهي تُسمّي الوجه الذي صار إليه السطر:
 * هويته هي صنفه واختياراته معاً، لا ملاحظته — لأن الاختيار شيء يُسأل عنه الزبون فيردّ
 * صفي السلة والطلب، والملاحظة كلام حرّ يُقرأ فوق الصنف نفسه. ولو كانت الملاحظة تغيّر
 * الهوية لصار لكل حرف يكتبه سطر، والسطر الذي يتغيّر كل حرف فيه سطر لا يُقرأ.
 */
public class CustomerCart {

    private static final String PREFS = "customer_cart";

    private static final String FIELD_KEY = "key";
    private static final String FIELD_STORE_ID = "storeId";
    private static final String FIELD_PRODUCT_ID = "productId";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_QTY = "qty";
    private static final String FIELD_NOTE = "note";
    private static final String FIELD_OPTIONS = "options";

    private final SharedPreferences prefs;
    private final String key;

    /** هوية السطر إلى سطره، بترتيب الإضافة حتى تُقرأ اللائحة كما رآها الزبون */
    private final Map<String, Line> lines = new LinkedHashMap<>();

    /**
     * صنف واحد بوجه واحد في السلة. الوحدة العامة للملاحظة والاختيارات هي ما يجعل
     * عدّاد البطاقة وعدّاد النافذة يكتبان السطر نفسه لا سطرين متجاورين.
     */
    public static final class Line {

        private final String lineKey;
        private final String storeId;
        private final String productId;
        private final String name;
        private final List<String> options;
        private double price;
        private int qty;
        private String note;

        private Line(String lineKey, String storeId, String productId, String name,
                     double price, int qty, String note, List<String> options) {
            this.lineKey = lineKey;
            this.storeId = storeId;
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.qty = qty;
            this.note = note;
            this.options = options;
        }

        public String getKey() {
            return lineKey;
        }

        public String getStoreId() {
            return storeId;
        }

        public String getProductId() {
            return productId;
        }

        public String getName() {
            return name;
        }

        /** سعر الوحدة بما فيها الاختيارات، وهو ما يُضرب في العدد ولا يُعاد حسابه */
        public double getUnitPrice() {
            return price;
        }

        public int getQuantity() {
            return qty;
        }

        public String getNote() {
            return note;
        }

        @NonNull
        public List<String> getOptions() {
            return options;
        }
    }

    private CustomerCart(@NonNull Context context, @NonNull String customerUid) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.key = customerUid;
        read();
    }

    /** سلة هذا الزبون على هذا الجهاز: الكائن رخيص، فليُبنَ عند الحاجة لا ليُخزَّن */
    @NonNull
    public static CustomerCart forCustomer(@NonNull Context context, @NonNull String customerUid) {
        return new CustomerCart(context, customerUid);
    }

    /**
     * هوية السطر: الصنف ثم اختياراته بترتيب مجموعاتها كما كتبها التاجر. الترتيب
     * مقصود لأنه ما يراه الزبون، ولو عُكس لصار السطر سطرين بنفس المعنى.
     */
    @NonNull
    public static String keyOf(@NonNull String productId, @NonNull List<String> options) {
        StringBuilder built = new StringBuilder(productId);
        for (String option : options) {
            if (!option.isEmpty()) {
                built.append('|').append(option);
            }
        }
        return built.toString();
    }

    /** السطر الذي تفتحه البطاقة والنافذة معاً: لا اسم له بلا اختيارات، فهو الصنف نفسه */
    @NonNull
    public static String plainKey(@NonNull String productId) {
        return keyOf(productId, Collections.<String>emptyList());
    }

    /**
     * الكتابة الوحيدة في السلة: «السطر الذي كان عند هذه الهوية صار الآن بهذا الصنف
     * وهذه الاختيارات وهذه الملاحظة وهذا العدد».
     *
     * العدد مطلق لا زيادة: النافذة تعرض عدد السطر وتزيده وتنقصه، فالعدد الذي تمرّره
     * البطاقة والنافذة هو العدد الذي يراه الزبون. ولو صادف أن كان للوجه الجديد سطر
     * موجود غير السطر المنقول، اتّحدا سطراً واحداً بجمع العددين — لأن صنفاً بوجهٍ واحد
     * لا يكون سطرين في سلة واحدة.
     */
    public void writeVariant(@NonNull String fromKey, @NonNull Product product,
                             @NonNull List<String> options, @NonNull String note, int qty) {
        if (product.getId().isEmpty()) {
            return;
        }
        String toKey = keyOf(product.getId(), options);
        double unitPrice = product.priceWith(options);

        // وجه لم يتغيّر: السطر يُعدَّل في مكانه، لأن نقله إلى آخر اللائحة عند كل ضغطة
        // يعني أن سلة الزبون تُعاد ترتيبها وهو يعدّ طبقاً واحداً
        if (toKey.equals(fromKey)) {
            Line kept = lines.get(toKey);
            if (kept != null) {
                kept.qty = qty;
                kept.price = unitPrice;
                kept.note = note;
                if (qty <= 0) {
                    lines.remove(toKey);
                }
                write();
                return;
            }
        }

        lines.remove(fromKey);

        Line target = lines.get(toKey);
        if (target != null) {
            target.qty += qty;
            target.price = unitPrice;
            target.note = note;
            if (target.qty <= 0) {
                lines.remove(toKey);
            }
        } else if (qty > 0) {
            lines.put(toKey, new Line(toKey, product.getStoreId(), product.getId(),
                    product.getName(), unitPrice, qty, note, new ArrayList<>(options)));
        }
        write();
    }

    /**
     * عدّاد البطاقة: وحدة زيادة أو نقصان على وجه الصنف الذي في السلة. البطاقة لا تخترع
     * اختيارات لم تُعرض على الزبون، فتزيد ما أمامها وتنقصه كما هو — وأما الوجه الذي فيه
     * خبز ومضافات فلا يُكتب إلا من النافذة التي عرضتهما.
     */
    public void adjust(@NonNull Product product, int delta) {
        Line kept = lineOf(product.getId());
        if (kept == null) {
            if (delta > 0) {
                writeVariant(plainKey(product.getId()), product,
                        Collections.<String>emptyList(), "", delta);
            }
            return;
        }
        writeVariant(kept.lineKey, product, kept.options, kept.note, kept.qty + delta);
    }

    /**
     * آخر سطر لهذا الصنف: هو ما تفتحه النافذة، لأن الزبون يعدّل الوجه الذي تركه آخر
     * مرة لا وجهاً جديداً بجانبه. والصنف بلا سطر يُفتح وجهه العام بعد صفر.
     */
    @Nullable
    public Line lineOf(@NonNull String productId) {
        Line found = null;
        for (Line line : lines.values()) {
            if (productId.equals(line.productId)) {
                found = line;
            }
        }
        return found;
    }

    /** كم وحدة من هذا الصنف في السلة بكل أوجهه؟ هو العدد المكتوب على البطاقة والإطار معها */
    public int quantity(@NonNull String productId) {
        int count = 0;
        for (Line line : lines.values()) {
            if (productId.equals(line.productId)) {
                count += line.qty;
            }
        }
        return count;
    }

    /** عدد الوحدات كلها في كل المحلات، لا عدد الأصناف: وهو ما يقوله شريط السلة */
    public int itemCount() {
        int count = 0;
        for (Line line : lines.values()) {
            count += line.qty;
        }
        return count;
    }

    /** مجموع السلة كلها: الدفع مرة واحدة عند الخروج، فالشريط يقول الكل لا هذا المحل */
    public double total() {
        double sum = 0;
        for (Line line : lines.values()) {
            sum += line.price * line.qty;
        }
        return sum;
    }

    @NonNull
    public List<Line> getLines() {
        return new ArrayList<>(lines.values());
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** تفريغ السلة: تستدعيها شاشة الدفع بعد أن تُكتب الطلبات، ولا تُستدعي من هنا */
    public void clear() {
        lines.clear();
        write();
    }

    /**
     * قراءة السطور المحفوظة: سطر بلا هوية أو بعد يُتجاهل لا يُصلَح، لأن الإصلاح هنا
     * يعني اختراع صنف لم يضغط عليه أحد. وسلة لم تُكتب من قبل تكون فارغة بلا خطأ.
     */
    private void read() {
        lines.clear();

        String stored = prefs.getString(key, "");
        if (stored.isEmpty()) {
            return;
        }
        try {
            JSONArray saved = new JSONArray(stored);
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String productId = item.optString(FIELD_PRODUCT_ID, "");
                int qty = item.optInt(FIELD_QTY, 0);
                if (productId.isEmpty() || qty <= 0) {
                    continue;
                }

                List<String> options = new ArrayList<>();
                JSONArray savedOptions = item.optJSONArray(FIELD_OPTIONS);
                if (savedOptions != null) {
                    for (int o = 0; o < savedOptions.length(); o++) {
                        options.add(savedOptions.optString(o, ""));
                    }
                }

                String lineKey = item.optString(FIELD_KEY, "");
                if (lineKey.isEmpty()) {
                    lineKey = keyOf(productId, options);
                }
                lines.put(lineKey, new Line(lineKey, item.optString(FIELD_STORE_ID, ""),
                        productId, item.optString(FIELD_NAME, ""),
                        item.optDouble(FIELD_PRICE, 0), qty,
                        item.optString(FIELD_NOTE, ""), options));
            }
        } catch (org.json.JSONException ignored) {
            // سطر مكتوب بيد في الكونسول أو نسخة أقدم: السلة تُبدأ من جديد لا تتعطّل
            lines.clear();
        }
    }

    private void write() {
        JSONArray saved = new JSONArray();
        for (Line line : lines.values()) {
            JSONObject item = new JSONObject();
            try {
                item.put(FIELD_KEY, line.lineKey);
                item.put(FIELD_STORE_ID, line.storeId);
                item.put(FIELD_PRODUCT_ID, line.productId);
                item.put(FIELD_NAME, line.name);
                item.put(FIELD_PRICE, line.price);
                item.put(FIELD_QTY, line.qty);
                item.put(FIELD_NOTE, line.note);
                item.put(FIELD_OPTIONS, new JSONArray(line.options));
                saved.put(item);
            } catch (org.json.JSONException ignored) {
                // قيمة لا تُكتب في JSON لا تُكتب في السلة، والباقي يُحفظ كما هو
            }
        }
        prefs.edit().putString(key, saved.toString()).apply();
    }
}
