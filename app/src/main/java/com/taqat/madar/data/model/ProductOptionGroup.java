package com.taqat.madar.data.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * مجموعة اختيارات على صنف واحد: «نوع الخبز» أو «إضافات اختيارية» وما إليها.
 *
 * التاجر هو من يكتبها حين يضيف منتجه، وهي اختيارية تماماً — فمعظم الأصناف لا تحتاج
 * مجموعة واحدة، والصنف الذي لا يحتاجها تُفتح تفاصيله بلا قسم اختيارات أصلاً. ولا
 * مجموعة إجبارية عندنا: الزبون يستطيع أن يترك «نوع الخبز» بلا اختيار، وهو قرار اتُّفق
 * عليه لأن الإجبار يعني أن نختار له نحن حين يضغط الزائد على البطاقة من غير ما يفتح.
 *
 * multiple هي الفرق بين الشكلين في الرسمة: واحدة بدائرة اختيار (خبز صاج أو كيزر)،
 * وواحدة بمربّع اختيار يجمع ما شاء (ثومية وبطاطا ومخلل معاً). الحقل محفوظ لا محسوب،
 * لأن الرسمة فرّقتهما بوضوح ولا شيء في البيانات يقول أيّهما أيّهما.
 *
 * اسم الخيار هو مفتاحه في سلة الزبون وطلبه: السعر قد يتغيّر والاسم ما يتغيّر، وهو
 * الاسم الذي سيقرأه التاجر على طلبه — فلا معرّف داخلي يُختلق لما لا يحتاجه أحد.
 */
public class ProductOptionGroup {

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_MULTIPLE = "multiple";
    public static final String FIELD_ITEMS = "items";

    private static final String ITEM_NAME = "name";
    private static final String ITEM_PRICE = "price";

    private String title = "";

    /** true تعني أن الزبون يجمع ما شاء منها، وfalse تعني واحداً لا غير */
    private boolean multiple;

    private final List<Item> items = new ArrayList<>();

    public ProductOptionGroup() {
    }

    public ProductOptionGroup(@NonNull String title, boolean multiple) {
        this.title = title;
        this.multiple = multiple;
    }

    /** كتابة الحقل كما يُقبله Firestore: قائمة قوائم، لا كائن داخل كائن */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_TITLE, title);
        data.put(FIELD_MULTIPLE, multiple);

        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (Item item : items) {
            itemsData.add(item.toMap());
        }
        data.put(FIELD_ITEMS, itemsData);
        return data;
    }

    /**
     * قراءة مجموعة من الوثيقة. عنصر ليس خريطة يُتجاهل لا يُصلَح: مجموعة لم تُفهم
     * كلها تُترك بلا أصناف، فتعرض النافذة عنوانها وحده أو تختفي، ولا تُختلق إضافة
     * لم يطلبها تاجر.
     */
    @NonNull
    public static ProductOptionGroup fromMap(@NonNull Map<String, Object> data) {
        ProductOptionGroup group = new ProductOptionGroup();
        group.title = text(data.get(FIELD_TITLE));

        Object multipleValue = data.get(FIELD_MULTIPLE);
        group.multiple = multipleValue instanceof Boolean && (Boolean) multipleValue;

        Object itemsValue = data.get(FIELD_ITEMS);
        if (itemsValue instanceof List) {
            for (Object entry : (List<?>) itemsValue) {
                if (entry instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) entry;
                    group.items.add(Item.fromMap(itemMap));
                }
            }
        }
        return group;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    @NonNull
    public List<Item> getItems() {
        return items;
    }

    /** إضافة خيار: وحدها تُفرغ القائمة قبلها إن أراد التاجر أن يستبدل ما كتبه */
    public void addItem(@NonNull Item item) {
        items.add(item);
    }

    /** كم يزداد سعر الصنف لو اختيرت هذه القائمة؟ المجموع هو ما يراه الزبون في السطر */
    public double priceOf(@NonNull List<String> chosenNames) {
        double sum = 0;
        for (Item item : items) {
            if (chosenNames.contains(item.getName())) {
                sum += item.price;
            }
        }
        return sum;
    }

    /** خيار واحد باسمه وسعره، وهو ما ينسخه سطر السلة خلفه */
    public static class Item {

        private String name = "";
        private double price;

        public Item() {
        }

        public Item(@NonNull String name, double price) {
            this.name = name;
            this.price = price;
        }

        @NonNull
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put(ITEM_NAME, name);
            data.put(ITEM_PRICE, price);
            return data;
        }

        @NonNull
        public static Item fromMap(@NonNull Map<String, Object> data) {
            Item item = new Item();
            item.name = text(data.get(ITEM_NAME));

            Object priceValue = data.get(ITEM_PRICE);
            item.price = priceValue instanceof Number ? ((Number) priceValue).doubleValue() : 0;
            return item;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }
    }
}
