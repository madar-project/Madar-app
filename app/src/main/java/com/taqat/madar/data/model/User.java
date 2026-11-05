package com.taqat.madar.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * بيانات مستخدم مدار كما تُخزَّن في Firestore داخل مجموعة users.
 * أسماء الحقول ثابتة لأنها نفسها التي ستقرأها لوحة التحكم (الموقع).
 *
 * الإنسان الواحد قد يكون زبوناً وتاجراً ومندوباً في آن واحد، فالأدوار مصفوفة
 * تتراكم ولا تستبدل بعضھا: "زبون" منوحة لكل حساب بلا موافقة، أما الأدوار
 * التجارية فلا تدخل المصفوفة إلا بعد موافقة لوحة التحكم على ملف نشاطه.
 */
public class User {

    public static final String COLLECTION = "users";

    public static final String FIELD_FULL_NAME = "fullName";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_ROLES = "roles";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_EMAIL_VERIFIED = "emailVerified";
    public static final String FIELD_CREATED_AT = "createdAt";

    private String uid;
    private String fullName;
    private String email;
    private String phone;
    /** الدور المطلوب عند التسجيل، ويبقى مرجعاً لشاشة المراجعة قبل الموافقة */
    private Role role = Role.CUSTOMER;
    private AccountStatus status = AccountStatus.PENDING;
    /** الأدوار الممنوحة فعلاً، و"زبون" حاضر فيها دائماً */
    private final List<Role> roles = new ArrayList<>();
    private boolean emailVerified;

    public User() {
        roles.add(Role.CUSTOMER);
    }

    /**
     * إنشاء مستخدم جديد: الزبون يُقبل مباشرة، وباقي الأدوار تنتظر موافقة لوحة التحكم.
     */
    public User(String uid, String fullName, String email, String phone, Role role) {
        this();
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.status = role.requiresApproval() ? AccountStatus.PENDING : AccountStatus.APPROVED;

        if (role != Role.CUSTOMER) {
            roles.add(role);
        }
    }

    /** تحويل البيانات إلى خريطة تُكتب في Firestore */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_FULL_NAME, fullName);
        data.put(FIELD_EMAIL, email);
        data.put(FIELD_PHONE, phone);
        data.put(FIELD_ROLE, role.value());
        data.put(FIELD_ROLES, roleValues());
        data.put(FIELD_STATUS, status.value());
        data.put(FIELD_EMAIL_VERIFIED, emailVerified);
        return data;
    }

    /** قراءة البيانات القادمة من Firestore إلى كائن مستخدم */
    public static User fromMap(String documentId, Map<String, Object> data) {
        User user = new User();
        user.uid = documentId;
        if (data == null) {
            return user;
        }

        user.fullName = text(data.get(FIELD_FULL_NAME));
        user.email = text(data.get(FIELD_EMAIL));
        user.phone = text(data.get(FIELD_PHONE));
        user.role = Role.fromValue(text(data.get(FIELD_ROLE)));
        user.status = AccountStatus.fromValue(text(data.get(FIELD_STATUS)));
        user.emailVerified = Boolean.parseBoolean(text(data.get(FIELD_EMAIL_VERIFIED)));
        user.readRoles(data.get(FIELD_ROLES));
        return user;
    }

    /**
     * مصفوفة الأدوار المخزنة، مع رجوع آمن للوثائق القديمة التي كُتبت قبل وجود الحقل:
     * تُقرأ من حقل الدور المفرد، ويُضاف الزبون لأنه منوحة لكل حساب.
     */
    private void readRoles(Object value) {
        roles.clear();
        roles.add(Role.CUSTOMER);

        if (!(value instanceof List)) {
            if (role != Role.CUSTOMER) {
                roles.add(role);
            }
            return;
        }
        for (Object item : (List<?>) value) {
            Role parsed = Role.fromValue(text(item));
            if (!roles.contains(parsed)) {
                roles.add(parsed);
            }
        }
    }

    private List<String> roleValues() {
        List<String> values = new ArrayList<>();
        for (Role item : roles) {
            values.add(item.value());
        }
        return values;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public String getUid() {
        return uid;
    }

    public String getFullName() {
        return fullName;
    }

    /** البريد المستخدم للدخول، وهو نفسه المحفوظ في المصادقة */
    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    /** الأدوار الممنوحة للمستخدم، مرتبة كما كُتبت في الوثيقة */
    public List<Role> getRoles() {
        return new ArrayList<>(roles);
    }

    /**
     * هل يملك هذا الدور فعلاً؟ الدور التجاري لا يُمنح إلا بعد موافقة لوحة التحكم،
     * أما {@link Role#CUSTOMER} فممنوح لكل حساب بلا استثناء.
     */
    public boolean hasRole(Role target) {
        return roles.contains(target);
    }

    /** هل هو زبون فقط، فلا يحتاج مفتاح تبديل أدوار في واجهته؟ */
    public boolean isCustomerOnly() {
        return roles.size() == 1 && roles.get(0) == Role.CUSTOMER;
    }

    /**
     * هل يمكن للمستخدم دخول التطبيق الآن؟ تُقرأ من حالة الدور المطلوب،
     * وتتحول لاحقاً إلى سؤال عن وجود دور ممنوح غير الزبون.
     */
    public boolean canEnterApp() {
        return status == AccountStatus.APPROVED;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /** هل ضغط المستخدم على رابط تأكيد البريد؟ */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
