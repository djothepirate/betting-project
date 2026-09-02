package com.bettingproject.collection.adapter.web.j7;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/** Versioned HTTP constants for the optional J7 canonical export import protocol. */
public final class J7ImportHttpContract {

    public static final String METHOD = "POST";
    public static final String PATH = "/api/imports/sofascore/j7-canonical-events";
    public static final String REQUEST_MEDIA_TYPE =
            "application/vnd.betting-project.j7-canonical-event+json;version=1.0";
    public static final String ACK_MEDIA_TYPE =
            "application/vnd.betting-project.j7-delivery-ack+json;version=1.0";
    public static final String PROTOCOL_VERSION = "1.0";
    public static final String SCHEMA_ID =
            "urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String SCHEMA_RESOURCE =
            "/schemas/j7-canonical-event-export-v1.schema.json";
    public static final int MAXIMUM_REQUEST_BYTES = 5_242_880;
    public static final int MAXIMUM_ACK_BYTES = 16_384;

    public static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCEPT = "Accept";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String PROTOCOL_VERSION_HEADER = "X-J7-Protocol-Version";
    public static final String EXPORT_ID = "X-J7-Export-Id";
    public static final String FILE_SHA256 = "X-J7-File-SHA256";
    public static final String DATA_SHA256 = "X-J7-Data-SHA256";

    public static final String SHA_256_REGEX = "[0-9a-f]{64}";
    public static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    public static final String IDEMPOTENCY_KEY_REGEX =
            "j7:" + UUID_REGEX + ":sha256:" + SHA_256_REGEX;
    public static final Pattern SHA_256_PATTERN = Pattern.compile(SHA_256_REGEX);
    public static final Pattern UUID_PATTERN = Pattern.compile(UUID_REGEX);
    public static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile(IDEMPOTENCY_KEY_REGEX);

    private static final UrlPathHelper URL_PATH_HELPER = UrlPathHelper.defaultInstance;

    /**
     * Matches the route as Spring MVC sees it so encoded and matrix-parameter aliases cannot skip
     * the receiver filters. The filters still require the externally supplied URI to equal
     * {@link #PATH} byte-for-byte before accepting a request.
     */
    public static boolean belongsToReceiverRouteFamily(HttpServletRequest request) {
        try {
            String lookupPath = URL_PATH_HELPER.getLookupPathForRequest(request);
            return PATH.equals(lookupPath) || lookupPath.startsWith(PATH + "/");
        }
        catch (IllegalArgumentException exception) {
            // A path which cannot be normalized safely is filtered and rejected fail-closed.
            return true;
        }
    }

    private J7ImportHttpContract() {
    }
}
