package com.bettingproject.collection.adapter.web.j7;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.bettingproject.collection.adapter.configuration.J7ClientCertificateFilter;
import com.bettingproject.collection.application.imports.J7ImportCommand;
import com.bettingproject.collection.application.imports.J7ImportResult;
import com.bettingproject.collection.application.imports.J7ImportService;
import com.bettingproject.collection.application.imports.StoredJ7Import;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("control-api")
@ConditionalOnProperty(
        prefix = "betting.integration.j7-receiver",
        name = "enabled",
        havingValue = "true")
public class J7ImportController {

    private static final MediaType ACK_MEDIA_TYPE = MediaType.parseMediaType(
            J7ImportHttpContract.ACK_MEDIA_TYPE);

    private final StrictJ7ImportParser parser;
    private final J7ImportService service;

    public J7ImportController(StrictJ7ImportParser parser, J7ImportService service) {
        this.parser = parser;
        this.service = service;
    }

    @PostMapping(J7ImportHttpContract.PATH)
    public ResponseEntity<byte[]> receive(
            HttpServletRequest request,
            @RequestBody byte[] body) {
        J7ImportCommand command = parser.parse(headers(request), body);
        String certificateSha256 = certificateSha256(request);
        J7ImportResult result = service.receive(command, certificateSha256);
        if (result instanceof J7ImportResult.Imported imported) {
            return positiveResponse(201, imported.receipt(),
                    J7DeliveryAcknowledgement.Status.IMPORTED);
        }
        if (result instanceof J7ImportResult.Duplicate duplicate) {
            return positiveResponse(200, duplicate.receipt(),
                    J7DeliveryAcknowledgement.Status.DUPLICATE);
        }
        throw J7ImportWebException.conflict();
    }

    private ResponseEntity<byte[]> positiveResponse(
            int httpStatus,
            StoredJ7Import receipt,
            J7DeliveryAcknowledgement.Status status) {
        J7DeliveryAcknowledgement acknowledgement = new J7DeliveryAcknowledgement(
                J7ImportHttpContract.PROTOCOL_VERSION,
                receipt.id(),
                status,
                receipt.exportId(),
                receipt.fileSha256(),
                receipt.dataSha256(),
                receipt.receivedAt());
        return ResponseEntity.status(httpStatus)
                .contentType(ACK_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                .body(J7DeliveryAcknowledgementCodec.encode(acknowledgement));
    }

    private String certificateSha256(HttpServletRequest request) {
        Object value = request.getAttribute(
                J7ClientCertificateFilter.CLIENT_CERTIFICATE_SHA256_ATTRIBUTE);
        if (value instanceof String sha256) {
            return sha256;
        }
        throw J7ImportWebException.forbidden();
    }

    private Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(name, List.copyOf(Collections.list(request.getHeaders(name)))));
        return headers;
    }
}
