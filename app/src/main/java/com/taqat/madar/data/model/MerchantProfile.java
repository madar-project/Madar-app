package com.taqat.madar.data.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ملف متجر كما يُخزَّن في مجموعة merchants، ومفتاح الوثيقة هو معرّف حساب صاحب المتجر.
 *
 * الوثيقة الواحدة تخدم غرضين: وهي التي تراجعها لوحة التحكم ما دامت غير معتمدة،
 * وهي نفسها واجهة المتجر التي يقرأها الزبائن بعد الاعتماد. لذلك تُعرض على الزبائن
 * فقط عندما يكون status برابر approved، وهو ما تفرضه قواعد الأمان لا التطبيق.
 *
 * لا رقم جوال هنا: رقم الجوال الذي يُطلب في تسجيل التاجر هو رقم صاحبه نفسه،
 * فيُحفظ مع الحساب في users وتبقى شاشة المتجر لبيانات النشاط فقط.
 */
public class MerchantProfile {

    public static final String COLLECTION = "merchants";

    public static final String FIELD_STORE_NAME = "storeName";
    public static final String FIELD_CATEGORY = "category";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_OPENING_TIME = "openingTime";
    public static final String FIELD_CLOSING_TIME = "closingTime";
    public static final String FIELD_LOGO_PATH = "logoPath";
    public static final String FIELD_LOGO_URL = "logoUrl";
    public static final String FIELD_PHOTO_PATHS = "photoPaths";
    public static final String FIELD_PHOTO_URLS = "photoUrls";
    public static final String FIELD_PAYMENT_METHODS = "paymentMethods";
    public static final String FIELD_ACCEPTING_ORDERS = "acceptingOrders";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_SUBMITTED_AT = "submittedAt";

    /** أقصى عدد من صور المعرض يُقبل، وهو ما يحدّه زر الاختيار في الشاشة */
    public static final int MAX_PHOTOS = 6;

    /** معرّف حساب صاحب المتجر، وهو نفسه مفتاح الوثيقة */
    private String uid;
    private String storeName = "";
    /** تصنيف المتجر، ويُخزَّن كنص ثابت اختاره التاجر من قائمة موحّدة ليتصفّح الزبائن على أساسه */
    private String category = "";
    /** العنوان النصي: اسم الشارع أو المعلم المميز، ويُستخدم لحين إضافة إحداثيات على الخريطة */
    private String address = "";
    /** نبذة قصيرة يقرأها الزبون في بطاقة المتجر */
    private String description = "";
    /** وقت الفتح ووقت الإغلاق بصيغة 24 ساعة، "09:00" و "23:00" مثلاً */
    private String openingTime = "";
    private String closingTime = "";
    /** شعار المتجر: يظهر دائرياً في بطاقته، مستقل عن صور المعرض */
    private String logoPath = "";
    private String logoUrl = "";
    /**
     * صور المعرض، ومساراتها في قائمة موازية.
     *
     * المسار هو معرّف الصورة لدى Cloudinary، وهو وحده ما يسمح بحذفها؛ والرابط هو ما يُعرض.
     */
    private final List<String> photoPaths = new ArrayList<>();
    private final List<String> photoUrls = new ArrayList<>();
    /** طرق الدفع التي يستقبلها المتجر، نصوص عربية كما اختارها التاجر */
    private final List<String> paymentMethods = new ArrayList<>();
    /**
     * هل المحل مفتوح ويستقبل طلبات؟ السويتش في أعلى واجهة التاجر هو ما يحرّكه،
     * وهو مستقل عن حالة الاعتماد: متجر مغلق مؤقتاً يبقى متجراً معتمداً.
     */
    private boolean acceptingOrders = true;
    private AccountStatus status = AccountStatus.PENDING;

    public MerchantProfile() {
    }

    public MerchantProfile(String uid, String storeName, String category, String address) {
        this.uid = uid;
        this.storeName = storeName;
        this.category = category;
        this.address = address;
    }

    /** تحويل البيانات إلى خريطة تُكتب في Firestore، بلا تاريخ إرسال لأن الحفظ يضيفه */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_STORE_NAME, storeName);
        data.put(FIELD_CATEGORY, category);
        data.put(FIELD_ADDRESS, address);
        data.put(FIELD_DESCRIPTION, description);
        data.put(FIELD_OPENING_TIME, openingTime);
        data.put(FIELD_CLOSING_TIME, closingTime);
        data.put(FIELD_LOGO_PATH, logoPath);
        data.put(FIELD_LOGO_URL, logoUrl);
        data.put(FIELD_PHOTO_PATHS, new ArrayList<>(photoPaths));
        data.put(FIELD_PHOTO_URLS, new ArrayList<>(photoUrls));
        data.put(FIELD_PAYMENT_METHODS, new ArrayList<>(paymentMethods));
        data.put(FIELD_ACCEPTING_ORDERS, acceptingOrders);
        data.put(FIELD_STATUS, status.value());
        return data;
    }

    /** قراءة بيانات قادمة من Firestore إلى ملف متجر */
    @NonNull
    public static MerchantProfile fromMap(String documentId, Map<String, Object> data) {
        MerchantProfile profile = new MerchantProfile();
        profile.uid = documentId;
        if (data == null) {
            return profile;
        }

        profile.storeName = text(data.get(FIELD_STORE_NAME));
        profile.category = text(data.get(FIELD_CATEGORY));
        profile.address = text(data.get(FIELD_ADDRESS));
        profile.description = text(data.get(FIELD_DESCRIPTION));
        profile.openingTime = text(data.get(FIELD_OPENING_TIME));
        profile.closingTime = text(data.get(FIELD_CLOSING_TIME));
        profile.logoPath = text(data.get(FIELD_LOGO_PATH));
        profile.logoUrl = text(data.get(FIELD_LOGO_URL));
        profile.photoPaths.addAll(textList(data.get(FIELD_PHOTO_PATHS)));
        profile.photoUrls.addAll(textList(data.get(FIELD_PHOTO_URLS)));
        profile.paymentMethods.addAll(textList(data.get(FIELD_PAYMENT_METHODS)));
        // المتاجر القديمة كُتبت قبل وجود الحقل، وأصحابها مفتوحون ما لم يقولوا غير ذلك
        Object ordersSwitch = data.get(FIELD_ACCEPTING_ORDERS);
        profile.acceptingOrders = ordersSwitch instanceof Boolean ? (Boolean) ordersSwitch : true;
        profile.status = AccountStatus.fromValue(text(data.get(FIELD_STATUS)));
        return profile;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /** قوائم Firestore قد تصل null أو بعناصر ليست نصاً، فنمرّ عليها بلا أن نفشل */
    @NonNull
    private static List<String> textList(Object value) {
        List<String> values = new ArrayList<>();
        if (!(value instanceof List)) {
            return values;
        }
        for (Object item : (List<?>) value) {
            if (item != null) {
                values.add(item.toString());
            }
        }
        return values;
    }

    public String getUid() {
        return uid;
    }

    public String getStoreName() {
        return storeName;
    }

    public String getCategory() {
        return category;
    }

    public String getAddress() {
        return address;
    }

    public String getDescription() {
        return description;
    }

    public String getOpeningTime() {
        return openingTime;
    }

    public String getClosingTime() {
        return closingTime;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public boolean hasLogo() {
        return !logoUrl.isEmpty();
    }

    @NonNull
    public List<String> getPhotoPaths() {
        return photoPaths;
    }

    @NonNull
    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    /** هل رفع للتاجر صورة واحدة على الأقل؟ بدونها لا يُقبل الطلب لأن الصور هي دليل المراجعة */
    public boolean hasPhotos() {
        return !photoUrls.isEmpty();
    }

    @NonNull
    public List<String> getPaymentMethods() {
        return paymentMethods;
    }

    public boolean isAcceptingOrders() {
        return acceptingOrders;
    }

    public void setAcceptingOrders(boolean acceptingOrders) {
        this.acceptingOrders = acceptingOrders;
    }

    public AccountStatus getStatus() {
        return status;
    }

    /** هل المتجر معتمد ويظهر للزبائن؟ */
    public boolean isApproved() {
        return status == AccountStatus.APPROVED;
    }

    /**
     * هل المتجر داخل ساعات عمله الآن؟ تُحفظ الساعات بصيغة 24 ساعة ("09:00")،
     * فالسؤال بالدقائق لا بالنص حتى لا يُقارن "23:00" مع "9:00" مقارنة حروف.
     *
     * النطاق الذي يعدّي منتصف الليل (يفتح 18:00 ويغلق 02:00) محسوب كذلك، لأن
     * مقاهي كثيرة تعمل به، ولو تُرِك للشرط العادي لأغلق نفسه طوال الليل.
     *
     * متجر بلا ساعات محفوظ يُعدّ مفتوحاً: الحقل اختياري في نموذج التسجيل، ولا يحوز
     * أن يُحجب عن الزبائن عقوبة على تركه فارغاً. وإغلاق التاجر بيده حقل مستقل هو
     * acceptingOrders، فالاثنان يُقالان للزبون كلمتين مختلفتين لا كلمة واحدة.
     */
    public boolean isOpenNow() {
        int open = minutesOf(openingTime);
        int close = minutesOf(closingTime);
        if (open < 0 || close < 0) {
            return true;
        }

        Calendar clock = Calendar.getInstance();
        int now = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE);

        return open <= close
                ? now >= open && now < close
                : now >= open || now < close;
    }

    /** "HH:mm" إلى دقائق من منتصف الليل، و-1 لكل نص خارج الصيغة كقيمة كُتبت في الكونسول */
    private static int minutesOf(@NonNull String time) {
        String[] parts = time.split(":");
        if (parts.length < 2) {
            return -1;
        }
        try {
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = Integer.parseInt(parts[1].trim());
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                return -1;
            }
            return hours * 60 + minutes;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setOpeningTime(String openingTime) {
        this.openingTime = openingTime;
    }

    public void setClosingTime(String closingTime) {
        this.closingTime = closingTime;
    }

    public void setLogo(String logoPath, String logoUrl) {
        this.logoPath = logoPath == null ? "" : logoPath;
        this.logoUrl = logoUrl == null ? "" : logoUrl;
    }

    /** استبدال كامل صور المعرض بدفعة واحدة، فلا يبقى صورة قديمة بلا مسارها */
    public void setPhotos(@NonNull List<String> paths, @NonNull List<String> urls) {
        photoPaths.clear();
        photoPaths.addAll(paths);
        photoUrls.clear();
        photoUrls.addAll(urls);
    }

    public void setPaymentMethods(@NonNull List<String> methods) {
        paymentMethods.clear();
        paymentMethods.addAll(methods);
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
