package com.taqat.madar.data.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * طلب زبون على متجر.
 *
 * أصناف الطلب **منسوخة** (اسم وسعر) لا محال مرجع إلى products: الزبون اتفق على ما
 * شافه لحظة الطلب، فلو رفع التاجر سعر طبقه بعدين ما ينعاد حساب فاتورة ماضية، ولو
 * حذف الطبق من المنيو يبقى اللي طلبه الزبون مقروء قدام التاجر. هذا فرق مقصود عن
 * العروض، اللي بتشتغل بالمعرّفات لأنها بتتغيّر مع الطبق لا قبله.
 *
 * التاجر يملك من الحالة انتقالين وحدهم: جديد ← قيد التحضير بالقبول، وجديد ← مرفوض
 * بالرفض. ما بعدهم (جاهز، مكتمل) يحرّكه المندوب أو الزبون لمّا تتبنى شاشاتهم،
 * فما بيكتبهم التطبيق من هنا.
 *
 * رقم الطلب متسلسل لكل متجر من واحد، ويُسنَد وقت الإنشاء لا هنا: الشاشة دي بتقرأه
 * وبس، واللي بكتبه هو مسار طلب الزبون.
 */
public class Order {

    public static final String COLLECTION = "orders";

    public static final String FIELD_STORE_ID = "storeId";
    public static final String FIELD_CUSTOMER_UID = "customerUid";
    public static final String FIELD_CUSTOMER_NAME = "customerName";
    public static final String FIELD_CUSTOMER_PHONE = "customerPhone";
    public static final String FIELD_NUMBER = "number";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_PLACED_AT = "placedAt";
    public static final String FIELD_DELIVERY = "delivery";
    public static final String FIELD_ITEMS = "items";
    public static final String FIELD_TOTAL = "total";
    public static final String FIELD_REJECT_REASON = "rejectReason";
    public static final String FIELD_REJECT_NOTE = "rejectNote";

    /** القيم المحفوظة كما هي في الوثيقة، والنص العربي اللي يقابها في strings.xml */
    public static final String STATUS_NEW = "new";
    public static final String STATUS_PREPARING = "preparing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_REJECTED = "rejected";

    /**
     * أسباب الرفض الأربعة كما في نافذتها. القيم بالإنجليزية زي الحالات، والنص
     * العربي اللي يقابها في strings.xml: شاشة الزبون بتقرأ السبب وتختار له جملة
     * بلغتها، فما تنكتبش العبارة المحكية نفسها في الوثيقة.
     */
    public static final String REASON_OUT_OF_STOCK = "out_of_stock";
    public static final String REASON_BUSY = "busy";
    public static final String REASON_CLOSED = "closed";
    public static final String REASON_OTHER = "other";

    /** مفاتيح الصنف داخل مصفوفة items */
    public static final String ITEM_NAME = "name";
    public static final String ITEM_NOTE = "note";
    public static final String ITEM_QUANTITY = "quantity";
    public static final String ITEM_PRICE = "unitPrice";

    private String id = "";
    private String storeId = "";
    private String customerUid = "";
    private String customerName = "";
    private String customerPhone = "";

    /** الرقم اللي بيشوفه الزبون والتاجر: #2088 */
    private int number;

    /** واحد من ثوابت STATUS_، والفارغ يُقرأ جديد لأن الوثيقة لسه ما اتحرّكتش */
    private String status = STATUS_NEW;

    /** لحظة الطلب بالمللي ثانية، تُبنى عليها «اليوم 12:40 م» و«منذ 6 دقائق» */
    private long placedAt;

    /** طريقة الاستلام كما اختارها الزبون: «توصيل سريع» أو «استلام من المتجر» */
    private String delivery = "";

    /** أصناف الطلب منسوخة لحظة الشراء */
    private final List<Line> lines = new ArrayList<>();

    /** المجموع كما حُسب وقت الطلب. محفوظ لا يُعاد حسابه كل مرة عشان الفاتورة ما تتحرّكش */
    private double total;

    /** واحد من ثوابت REASON_، وفاضي طول ما الطلب ما اترفضش */
    private String rejectReason = "";

    /** الملاحظة اللي كتبها التاجر للعميل وقت الرفض، واختيارية كما في التصميم */
    private String rejectNote = "";

    public Order() {
    }

    /** خريطة الكتابة إلى Firestore. لا يُستدعى من شاشة التاجر: هو إنشاء، والإنشاء للزبون */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_STORE_ID, storeId);
        data.put(FIELD_CUSTOMER_UID, customerUid);
        data.put(FIELD_CUSTOMER_NAME, customerName);
        data.put(FIELD_CUSTOMER_PHONE, customerPhone);
        data.put(FIELD_NUMBER, number);
        data.put(FIELD_STATUS, status);
        data.put(FIELD_DELIVERY, delivery);
        data.put(FIELD_TOTAL, total);
        data.put(FIELD_REJECT_REASON, rejectReason);
        data.put(FIELD_REJECT_NOTE, rejectNote);

        List<Map<String, Object>> linesData = new ArrayList<>();
        for (Line line : lines) {
            linesData.add(line.toMap());
        }
        data.put(FIELD_ITEMS, linesData);
        return data;
    }

    /** قراءة بيانات قادمة من Firestore إلى طلب */
    @NonNull
    public static Order fromMap(@NonNull String documentId, Map<String, Object> data) {
        Order order = new Order();
        order.id = documentId;
        if (data == null) {
            return order;
        }

        order.storeId = text(data.get(FIELD_STORE_ID));
        order.customerUid = text(data.get(FIELD_CUSTOMER_UID));
        order.customerName = text(data.get(FIELD_CUSTOMER_NAME));
        order.customerPhone = text(data.get(FIELD_CUSTOMER_PHONE));
        order.delivery = text(data.get(FIELD_DELIVERY));

        Object numberValue = data.get(FIELD_NUMBER);
        order.number = numberValue instanceof Number ? ((Number) numberValue).intValue() : 0;

        String statusValue = text(data.get(FIELD_STATUS));
        order.status = statusValue.isEmpty() ? STATUS_NEW : statusValue;

        Object totalValue = data.get(FIELD_TOTAL);
        order.total = totalValue instanceof Number ? ((Number) totalValue).doubleValue() : 0;

        order.rejectReason = text(data.get(FIELD_REJECT_REASON));
        order.rejectNote = text(data.get(FIELD_REJECT_NOTE));

        Object linesValue = data.get(FIELD_ITEMS);
        if (linesValue instanceof List) {
            for (Object entry : (List<?>) linesValue) {
                if (entry instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> lineMap = (Map<String, Object>) entry;
                    order.lines.add(Line.fromMap(lineMap));
                }
            }
        }
        return order;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    /** هل الطلب خلّى شاشات التاجر والنشط؟ المرفوض والمكتمل روّاح الأرشيف */
    public boolean isArchived() {
        return STATUS_COMPLETED.equals(status) || STATUS_REJECTED.equals(status);
    }

    /** هل للتاجر عليه عمل؟ العمل الوحيد المتبقي له هو قبول أو رفض طلب جديد */
    public boolean isAwaitingMerchant() {
        return STATUS_NEW.equals(status);
    }

    /** عدد الأصناف لا عدد القطع: «2 صنف» في التصميم يعني صنفين ولو بكمية مختلفة */
    public int getLineCount() {
        return lines.size();
    }

    /**
     * المجموع المعروض: المحفوظ أول، ولو ما انكتبش يُحسب من الأصناف. الوثيقة اللي
     * تُكتب يدويًا في الكونسول للتجربة غالباً تنسى الحقل، والشاشة ما بتسقطش عليه.
     */
    public double displayTotal() {
        if (total > 0) {
            return total;
        }
        double sum = 0;
        for (Line line : lines) {
            sum += line.getUnitPrice() * line.getQuantity();
        }
        return sum;
    }

    public String getId() {
        return id;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getCustomerUid() {
        return customerUid;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public int getNumber() {
        return number;
    }

    public String getStatus() {
        return status;
    }

    public long getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(long placedAt) {
        this.placedAt = placedAt;
    }

    public String getDelivery() {
        return delivery;
    }

    @NonNull
    public List<Line> getLines() {
        return lines;
    }

    public double getTotal() {
        return total;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getRejectNote() {
        return rejectNote;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason == null ? "" : rejectReason;
    }

    public void setRejectNote(String rejectNote) {
        this.rejectNote = rejectNote == null ? "" : rejectNote;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId == null ? "" : storeId;
    }

    public void setCustomerUid(String customerUid) {
        this.customerUid = customerUid == null ? "" : customerUid;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName == null ? "" : customerName;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone == null ? "" : customerPhone;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isEmpty() ? STATUS_NEW : status;
    }

    public void setDelivery(String delivery) {
        this.delivery = delivery == null ? "" : delivery;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public void addLine(@NonNull Line line) {
        lines.add(line);
    }

    /**
     * صنف واحد من طلب: منسوخ لا مستند. الكمية عدد القطع، والملاحظة هي اللي كتبها
     * الزبون («بدون ثوم، مخلل إضافي») فلو ما عنده ما تفضل السطر الثاني فاضي.
     */
    public static class Line {

        private String name = "";
        private String note = "";
        private int quantity = 1;
        private double unitPrice;

        public Line() {
        }

        public Line(String name, String note, int quantity, double unitPrice) {
            this.name = name == null ? "" : name;
            this.note = note == null ? "" : note;
            this.quantity = quantity < 1 ? 1 : quantity;
            this.unitPrice = unitPrice;
        }

        @NonNull
        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<>();
            data.put(ITEM_NAME, name);
            data.put(ITEM_NOTE, note);
            data.put(ITEM_QUANTITY, quantity);
            data.put(ITEM_PRICE, unitPrice);
            return data;
        }

        @NonNull
        public static Line fromMap(@NonNull Map<String, Object> data) {
            Line line = new Line();
            line.name = text(data.get(ITEM_NAME));
            line.note = text(data.get(ITEM_NOTE));

            Object quantity = data.get(ITEM_QUANTITY);
            line.quantity = quantity instanceof Number
                    ? Math.max(1, ((Number) quantity).intValue()) : 1;

            Object price = data.get(ITEM_PRICE);
            line.unitPrice = price instanceof Number ? ((Number) price).doubleValue() : 0;
            return line;
        }

        public String getName() {
            return name;
        }

        public String getNote() {
            return note;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }
    }
}
