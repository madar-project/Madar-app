package com.taqat.madar.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * منتج واحد في منيو التاجر: وجبة أو سلعة يستطيع الزبون طلبها.
 *
 * المنتج لا يُعرَّف بصاحبه من معرّف وثيقته، لأن مجموعة products مستقلة تحمل حقل storeId:
 * الزبون يجيب منتجات متجر بفلترة على هذا الحقل، ولوحة التحكم تعدّ منتجات كل المتاجر
 * بطلب واحد، وحذف حساب مرفوض يمرّ هنا بحثاً عن منتجات صاحبه.
 *
 * المنتج لا يحتاج اعتماداً من الإدارة: الاعتماد قرار على التاجر نفسه يُؤخذ مرة واحدة،
 * فما دام حسابه معتمداً تُنشر منتجاته مباشرة.
 */
public class Product {

    public static final String COLLECTION = "products";

    public static final String FIELD_STORE_ID = "storeId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_PREP_TIME = "prepTime";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_AVAILABLE = "available";
    public static final String FIELD_PHOTO_PATH = "photoPath";
    public static final String FIELD_PHOTO_URL = "photoUrl";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_OPTIONS = "options";

    /** أقل سعر يُقبل: منتج بسعر صفر غير قابل للطلب، وقد يكون خطأ إدخال لا نية */
    public static final double MIN_PRICE = 0.01;

    /** اسم المنتج كما يظهر للزبون */
    private String name = "";

    /** التصنيف نص عربي كما هو في قائمة القوائم، وهو نفسه المحفوظ */
    private String category = "";

    /** السعر بالشيكل: سوقنا يبيع به، وهو ما يراه الزبون في خانة السعر */
    private double price;

    /**
     * نطاق وقت التحضير بالدقائق محفوظاً كنص واحد مثل "15-25"، لأن التاجر يختار
     * نطاقاً جاهزاً لا رقماً، والزبون يقرأ «بين ١٥ و٢٥ دقيقة».
     */
    private String prepTime = "";

    /** وصف المنتج ومكوناته، اختياري لأنه ما من منتج لا يمكن طلبه بدونه */
    private String description = "";

    /**
     * جاهزية العرض: مطفأ يعني أن المنتج يبقى في المنيو لكنه غير متاح للطلب الآن،
     * كطبق نفد من المطبخ. وهو قرار التاجر وحده، لا حالة اعتماد.
     */
    private boolean available = true;

    /** معرّف الصورة لدى Cloudinary: وحده ما يسمح بحذفها، والرابط هو ما يُعرض */
    private String photoPath = "";
    private String photoUrl = "";

    /** معرّف الوثيقة، فارغ ما لم تُحفظ المنتج مرة */
    private String id = "";

    /** معرّف حساب صاحب المتجر، وهو مفتاح الفلترة والقاعدة الأمنية */
    private String storeId = "";

    /**
     * مجموعات الاختيار على الصنف، وهي اختيارية كالحقل نفسه: صنف بلا مجموعات يُفتح
     * تفاصيله بلا قسم اختيارات، ومعظم أصناف المنيو كذلك — فالطبق الذي لا يُطلب
     * بوجهين لا يحتاج من التاجر أن يخترع له وجوهاً.
     */
    private final List<ProductOptionGroup> options = new ArrayList<>();

    public Product() {
    }

    public Product(String storeId, String name, String category, double price) {
        this.storeId = storeId;
        this.name = name;
        this.category = category;
        this.price = price;
    }

    /** تحويل البيانات إلى خريطة تُكتب في Firestore، بلا تاريخ إنشاء لأن الحفظ يضيفه */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_STORE_ID, storeId);
        data.put(FIELD_NAME, name);
        data.put(FIELD_CATEGORY, category);
        data.put(FIELD_PRICE, price);
        data.put(FIELD_PREP_TIME, prepTime);
        data.put(FIELD_DESCRIPTION, description);
        data.put(FIELD_AVAILABLE, available);
        data.put(FIELD_PHOTO_PATH, photoPath);
        data.put(FIELD_PHOTO_URL, photoUrl);

        // نموذج التاجر ما زال لا يقرأ هذه المجموعات ولا يكتبها: حفظ تعديل على صنف اليوم
        // يمحوا ما كُتب في وثيقته من اختيارات، لأن الحفظ يكتب الوثيقة كلها
        // لا الحقل الذي غيّره التاجر وحده.
        // محرّر اختيارات التاجر هو ما يسدّ هذه الفرجة، فما يُعرض عليه يُرسل معه من جديد.
        List<Map<String, Object>> optionsData = new ArrayList<>();
        for (ProductOptionGroup group : options) {
            optionsData.add(group.toMap());
        }
        data.put(FIELD_OPTIONS, optionsData);
        return data;
    }

    /** قراءة بيانات قادمة من Firestore إلى منتج */
    @NonNull
    public static Product fromMap(@NonNull String documentId, Map<String, Object> data) {
        Product product = new Product();
        product.id = documentId;
        if (data == null) {
            return product;
        }

        product.storeId = text(data.get(FIELD_STORE_ID));
        product.name = text(data.get(FIELD_NAME));
        product.category = text(data.get(FIELD_CATEGORY));
        product.prepTime = text(data.get(FIELD_PREP_TIME));
        product.description = text(data.get(FIELD_DESCRIPTION));
        product.photoPath = text(data.get(FIELD_PHOTO_PATH));
        product.photoUrl = text(data.get(FIELD_PHOTO_URL));

        Object priceValue = data.get(FIELD_PRICE);
        product.price = priceValue instanceof Number ? ((Number) priceValue).doubleValue() : 0;

        // المنتجات الأولى كُتبت قبل وجود الحقل، وأصحابها يعرضونها ما لم يقولوا غير ذلك
        Object availableValue = data.get(FIELD_AVAILABLE);
        product.available = availableValue instanceof Boolean ? (Boolean) availableValue : true;

        Object optionsValue = data.get(FIELD_OPTIONS);
        if (optionsValue instanceof List) {
            for (Object entry : (List<?>) optionsValue) {
                if (entry instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> groupMap = (Map<String, Object>) entry;
                    product.options.add(ProductOptionGroup.fromMap(groupMap));
                }
            }
        }
        return product;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public String getId() {
        return id;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getPrice() {
        return price;
    }

    public String getPrepTime() {
        return prepTime;
    }

    /**
     * أول رقم في نطاق التحضير بالدقائق، وهو ما يُقارن به أسرع صنفين: النطاق محفوظ
     * نصاً مثل "15-25"، والمقارنة بالنص تجعل "+30" أسرع من "10-15" لأن علامة الزائد
     * تسبق الأرقام في الترتيب، والنتيجة أن يأخذ الزبون أبطأ طبق ويكتب له أسبق وقت.
     *
     * قيمة لا رقم فيها تُصوَّر أبطأ شيء، فلا تفوز على صنف محفوظ لمجرد أنها لم تُفهم.
     */
    public int prepStartMinutes() {
        return minutesOf(prepTime);
    }

    /** "15-25" إلى 15 و"+30" إلى 30، وأقصى القيمة لما لا رقم في النص إطلاقاً */
    public static int minutesOf(@Nullable String range) {
        if (range == null) {
            return Integer.MAX_VALUE;
        }

        int index = 0;
        while (index < range.length() && !isDigit(range.charAt(index))) {
            index++;
        }

        int value = 0;
        int digits = 0;
        while (index < range.length() && isDigit(range.charAt(index))) {
            value = value * 10 + (range.charAt(index) - '0');
            index++;
            digits++;
        }
        return digits == 0 ? Integer.MAX_VALUE : value;
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    public String getDescription() {
        return description;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    /** هل للمنتج صورة تُعرض؟ غيابها مقبول، فالزبون يطلب باسم الطبق وسعره */
    public boolean hasPhoto() {
        return !photoUrl.isEmpty();
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId == null ? "" : storeId;
    }

    /**
     * تعيين معرّف الوثيقة: وحده ما يفرّق بين «أنا منتج جديد» و«أنا عدّلت منتجاً موجوداً»,
     * فتعبئة نموذج التعديل به تجعل الحفظ فوق الوثيقة نفسها بدل أن ينشر نسخة مكررة.
     */
    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public void setCategory(String category) {
        this.category = category == null ? "" : category;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setPrepTime(String prepTime) {
        this.prepTime = prepTime == null ? "" : prepTime;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** الصورة تُستبدل كاملة بمعرّف ورابط: مسار بلا رابط لا يُعرض، ورابط بلا مسار لا يُحذف */
    public void setPhoto(String photoPath, String photoUrl) {
        this.photoPath = photoPath == null ? "" : photoPath;
        this.photoUrl = photoUrl == null ? "" : photoUrl;
    }

    @NonNull
    public List<ProductOptionGroup> getOptions() {
        return options;
    }

    /** استبدال المجموعات كلها: نموذج التاجر يبنيها من جديد في كل حفظ، فما يبقى منها قديم */
    public void setOptions(@NonNull List<ProductOptionGroup> groups) {
        options.clear();
        for (ProductOptionGroup group : groups) {
            if (!group.getTitle().isEmpty() && !group.getItems().isEmpty()) {
                options.add(group);
            }
        }
    }

    /** هل للصنف ما يُختار فيه؟ تجيبه النافذة قبل أن تقرر هل تُظهر قسم الاختيارات */
    public boolean hasOptions() {
        return !options.isEmpty();
    }

    /** سعر الصنف مع هذه الاختيارات: هو ما ينسخه سطر السلة، لا السعر الأصلي وحده */
    public double priceWith(@NonNull List<String> chosenNames) {
        double sum = price;
        for (ProductOptionGroup group : options) {
            sum += group.priceOf(chosenNames);
        }
        return sum;
    }
}
