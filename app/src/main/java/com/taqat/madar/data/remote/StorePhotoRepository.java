package com.taqat.madar.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * رفع صور المتاجر إلى Cloudinary.
 *
 * اخترنا Cloudinary بدل Firebase Storage لأن فيرمبا صارت تشترط خطة مدفوعة ببطاقة ائتمان
 * مقابل التخزين، بينما تكفي خطتنا المجانية هنا مئات آلاف الصور.
 *
 * اسم السحابة واسم إعداد الرفع ليسا سرّين: كلاهما يُرسل مع كل طلب رفع من الجهاز.
 * الأمان كله في أن الإعداد مقيّد — بلا مفتاح، وبمجلد واحد، وبصيغة صورة واحدة، وبحجم أقصى،
 * وبمعرّف عشوائي لا يُخمَّن فيستطيع غريب أن يكتب فوق صورة تاجر غيره.
 * أما المفتاح السري (API Secret) فلا يدخل التطبيق ولا يُرسل معه في أي طلب.
 */
public class StorePhotoRepository {

    /** اسم فلتر في Logcat لمتابعة رفع الصور */
    public static final String LOG_TAG = "MadarPhotos";

    /** اسم السحابة: لوحة Cloudinary → Settings → Upload → Cloud name */
    private static final String CLOUD_NAME = "ji4cpx8x";

    /** إعداد الرفع العام، وضعه Unsigned */
    private static final String UPLOAD_PRESET = "madar_store_photo";

    /** مجلد صور المتاجر في مكتبة الوسائط */
    private static final String FOLDER = "madar/storefronts";

    /**
     * صور المنتجات في مجلد منفصل: التاجر يبدّل صورة طبق دون أن يمس صور واجهة متجره،
     * وكل مجلد يحتاج إعداد رفع خاصاً به لأن الإعداد Unsigned مقيد بمجلد واحد.
     */
    private static final String PRODUCT_FOLDER = "madar/products";
    private static final String PRODUCT_PRESET = "madar_product_photo";

    private static final String UPLOAD_ENDPOINT =
            "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    /** نهاية السطر كما تفرضها ترويسة أجزاء الطلب */
    private static final String CRLF = "\r\n";

    /** مهلة بالمللي ثانية: الاتصال على شبكة بطيئة أفضل أن يفشل صريحاً من أن يعلّق */
    private static final int TIMEOUT_MS = 30_000;

    /** ما يرجعه الرفع الناجح: معرّف الصورة لحذفها لاحقاً، ورابطها للعرض */
    public static final class Uploaded {
        @NonNull public final String publicId;
        @NonNull public final String url;

        Uploaded(@NonNull String publicId, @NonNull String url) {
            this.publicId = publicId;
            this.url = url;
        }
    }

    /**
     * رفع صورة واحدة، معرّفة بوسم صاحبها، أو null عند الرفض أو الانقطاع.
     *
     * الدالة مانعة للاتصال: تفتح الطلب وتنتظر الرد، فلا تُستدعى من خيط الواجهة إطلاقاً.
     * لهذا تُنادى مرة بعد مرة داخل خيط خلفي واحد يرفع الشعار وصور المعرض بالترتيب،
     * فلو فشل رفع في الوسط يتوقف المسار كله بدل أن تُحفظ وثيقة ناقصة الصور.
     */
    @Nullable
    public Uploaded upload(@NonNull String uid, @NonNull byte[] jpeg) {
        return upload(jpeg, FOLDER, UPLOAD_PRESET, uid);
    }

    /** صورة منتج واحد، موسومة بصاحبها هو أيضاً حتى يمسحها سيرفر الإدارة بمسح الحساب */
    @Nullable
    public Uploaded uploadProduct(@NonNull String uid, @NonNull byte[] jpeg) {
        return upload(jpeg, PRODUCT_FOLDER, PRODUCT_PRESET, uid);
    }

    private Uploaded upload(@NonNull byte[] jpeg, @NonNull String folder,
                            @NonNull String preset, @NonNull String uid) {
        String boundary = "----Madar" + System.currentTimeMillis();
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(UPLOAD_ENDPOINT).openConnection();
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream body = new DataOutputStream(connection.getOutputStream())) {
                writeField(body, boundary, "upload_preset", preset);
                writeField(body, boundary, "folder", folder);
                writeField(body, boundary, "tags", "merchant_" + uid);
                writeImage(body, boundary, jpeg);
                body.writeBytes("--" + boundary + "--" + CRLF);
            }

            int status = connection.getResponseCode();
            String response = read(status >= HttpURLConnection.HTTP_BAD_REQUEST
                    ? connection.getErrorStream() : connection.getInputStream());

            if (status < HttpURLConnection.HTTP_BAD_REQUEST) {
                JSONObject json = new JSONObject(response);
                String publicId = json.optString("public_id");
                String secureUrl = json.optString("secure_url");
                if (!publicId.isEmpty() && !secureUrl.isEmpty()) {
                    return new Uploaded(publicId, secureUrl);
                }
            }

            Log.e(LOG_TAG, "Upload rejected (" + status + "): " + response);
            return null;
        } catch (Exception error) {
            Log.e(LOG_TAG, "Upload failed: " + error.getMessage(), error);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeField(DataOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF);
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(CRLF);
    }

    private void writeImage(DataOutputStream out, String boundary, byte[] jpeg) throws IOException {
        out.writeBytes("--" + boundary + CRLF);
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"store.jpg\"" + CRLF);
        out.writeBytes("Content-Type: image/jpeg" + CRLF + CRLF);
        out.write(jpeg);
        out.writeBytes(CRLF);
    }

    private String read(@Nullable InputStream input) throws IOException {
        if (input == null) {
            return "";
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, count);
        }
        input.close();
        return buffer.toString(StandardCharsets.UTF_8.name());
    }
}
