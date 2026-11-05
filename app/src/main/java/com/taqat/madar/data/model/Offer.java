package com.taqat.madar.data.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * عرض خصم يقيمه التاجر على أصناف من منيوه.
 *
 * العرض لا يلمس المنتج إطلاقاً: هو وثيقة مستقلة تحمل معرّفات الأصناف المشمولة، والسعر
 * المخفوض يُحسب حين يُعرض للزبون. فلو عدّل التاجر سعر الطبق بعد ذلك يبقى العرض صحيحاً،
 * ولو حُذف العرض لم يحتج المنتج لترميم سعره.
 *
 * لا حالة للعرض محفوظة: «مباشر» ما كان اليوم بين تاريخيه، وبينتهي لحاله بعد نهايته.
 * حقل حالة كان يعني وظيفة تنطفئ في الليل لتحدّثه، وواجهة تاجر تظن أن عرضاً ميتاً حيّ.
 */
public class Offer {

    public static final String COLLECTION = "offers";

    public static final String FIELD_STORE_ID = "storeId";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DISCOUNT_TYPE = "discountType";
    public static final String FIELD_VALUE = "value";
    public static final String FIELD_PRODUCT_IDS = "productIds";
    public static final String FIELD_STARTS_AT = "startsAt";
    public static final String FIELD_ENDS_AT = "endsAt";

    /** نسبة من السعر، أو مبلغ ثابت يُخصم منه — قرار التاجر في «نوع الخصم» */
    public static final String TYPE_PERCENT = "percent";
    public static final String TYPE_FIXED = "fixed";

    /** أقل خصم يُقبل: صفر يعني عرضاً لا يغيّر شيئاً، وغالبه خطأ إدخال */
    public static final double MIN_VALUE = 0.01;

    /**
     * أعلى نسبة يُقبل عليها: فوقها يصير الطبق عملياً مجاناً، والتاجر الذي يكتب ١٠٠٪
     * غالباً يقصد شيئاً غير ما كتبه. المبلغ الثابت لا يحتاج سقفًا لأنه يُقارن بالسعر.
     */
    public static final double MAX_PERCENT = 90;

    /** اسم العرض كما يظهر في بطاقته للزبون */
    private String id = "";
    private String storeId = "";
    private String name = "";

    /** واحد من TYPE_PERCENT أو TYPE_FIXED، والنص نفسه محفوظ في الوثيقة */
    private String discountType = TYPE_PERCENT;

    /** مقدار الخصم: نسبة عند TYPE_PERCENT، وشيكل عند TYPE_FIXED */
    private double value;

    /** معرّفات منتجات المتجر المشمولة، تُقرأ من products ولا تُنسخ منها */
    private final List<String> productIds = new ArrayList<>();

    /**
     * توقيتان بالمللي ثانية: بداية العرض من أول ثانية يوم البداية، ونهايته آخر ثانية
     * يوم النهاية — لأن التاجر يختار يوماً لا ساعة، ولو قورن منتصف الليل لكان آخر يوم
     * من العرض نصف يوم فقط.
     */
    private long startsAt;
    private long endsAt;

    public Offer() {
    }

    public Offer(String storeId, String name, String discountType, double value) {
        this.storeId = storeId;
        this.name = name;
        this.discountType = discountType;
        this.value = value;
    }

    /** خريطة الكتابة إلى Firestore: قائمة المعرّفات نسخة جديدة، لا ما يُعدّل بعد الكتابة */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_STORE_ID, storeId);
        data.put(FIELD_NAME, name);
        data.put(FIELD_DISCOUNT_TYPE, discountType);
        data.put(FIELD_VALUE, value);
        data.put(FIELD_PRODUCT_IDS, new ArrayList<>(productIds));
        data.put(FIELD_STARTS_AT, startsAt);
        data.put(FIELD_ENDS_AT, endsAt);
        return data;
    }

    /** قراءة بيانات قادمة من Firestore إلى عرض */
    @NonNull
    public static Offer fromMap(@NonNull String documentId, Map<String, Object> data) {
        Offer offer = new Offer();
        offer.id = documentId;
        if (data == null) {
            return offer;
        }

        offer.storeId = text(data.get(FIELD_STORE_ID));
        offer.name = text(data.get(FIELD_NAME));
        String type = text(data.get(FIELD_DISCOUNT_TYPE));
        offer.discountType = type.isEmpty() ? TYPE_PERCENT : type;

        Object valueNumber = data.get(FIELD_VALUE);
        offer.value = valueNumber instanceof Number ? ((Number) valueNumber).doubleValue() : 0;

        Object ids = data.get(FIELD_PRODUCT_IDS);
        if (ids instanceof List) {
            for (Object id : (List<?>) ids) {
                offer.productIds.add(text(id));
            }
        }

        offer.startsAt = number(data.get(FIELD_STARTS_AT));
        offer.endsAt = number(data.get(FIELD_ENDS_AT));
        return offer;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    /** هل العرض سارٍ الآن؟ تُقارَن لحظة واحدة، فاليومان محفوظان كبداية ونهاية يوم كاملين */
    public boolean isLive(long now) {
        return startsAt > 0 && now >= startsAt && now <= endsAt;
    }

    /** هل يشمل هذا المنتج؟ */
    public boolean covers(@NonNull String productId) {
        return productIds.contains(productId);
    }

    /**
     * السعر بعد الخصم. النسبة تُقرَض من السعر المخفوض لا تُضاف إليه، والمبلغ الثابت لا
     * ينزل به تحت قرش واحد: طبق مجاناً في الفاتورة ليس كرماً بل خطأ في الحساب.
     */
    public double discountedPrice(double price) {
        double cut = TYPE_PERCENT.equals(discountType)
                ? price * value / 100.0 : value;
        return Math.max(Product.MIN_PRICE, price - cut);
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

    public String getDiscountType() {
        return discountType;
    }

    public double getValue() {
        return value;
    }

    @NonNull
    public List<String> getProductIds() {
        return productIds;
    }

    public long getStartsAt() {
        return startsAt;
    }

    public long getEndsAt() {
        return endsAt;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId == null ? "" : storeId;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public void setDiscountType(String discountType) {
        this.discountType = TYPE_FIXED.equals(discountType) ? TYPE_FIXED : TYPE_PERCENT;
    }

    public void setValue(double value) {
        this.value = value;
    }

    /** القائمة تُستبدل محتوى لا مرجعاً، فلا يبقى العرض متصلاً بواجهة عدّلتها الشاشة */
    public void setProductIds(@NonNull List<String> ids) {
        productIds.clear();
        for (String id : ids) {
            if (id != null && !id.isEmpty() && !productIds.contains(id)) {
                productIds.add(id);
            }
        }
    }

    public void setStartsAt(long startsAt) {
        this.startsAt = startsAt;
    }

    public void setEndsAt(long endsAt) {
        this.endsAt = endsAt;
    }
}
