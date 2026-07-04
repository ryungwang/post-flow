package com.postflow.threads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Meta {@code signed_request} 파서/검증 — deauthorize·data-deletion 콜백이 앱시크릿으로 HMAC-SHA256 서명한
 * {@code <base64url_sig>.<base64url_payload>}를 검증하고 payload(JSON)를 돌려준다.
 * (스펙: sig = HMAC-SHA256(payload_문자열, app_secret), payload는 인코딩된 상태 그대로 서명 대상.)
 */
public final class MetaSignedRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private MetaSignedRequest() {
    }

    /** 검증 성공 시 payload JSON, 서명 불일치/형식오류면 null. */
    public static JsonNode verify(String signedRequest, String appSecret) {
        if (signedRequest == null || appSecret == null || appSecret.isBlank()) {
            return null;
        }
        int dot = signedRequest.indexOf('.');
        if (dot < 0) {
            return null;
        }
        String encodedSig = signedRequest.substring(0, dot);
        String payload = signedRequest.substring(dot + 1); // 서명 대상은 인코딩된 payload 문자열
        try {
            byte[] expected = hmacSha256(payload.getBytes(StandardCharsets.US_ASCII), appSecret);
            byte[] actual = URL_DECODER.decode(encodedSig);
            if (!MessageDigest.isEqual(expected, actual)) {
                return null;
            }
            return MAPPER.readTree(URL_DECODER.decode(payload));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hmacSha256(byte[] data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
    }
}
