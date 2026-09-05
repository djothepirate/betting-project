package com.bettingproject.collection.adapter.web.j7;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class J7DeliveryAcknowledgementCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private J7DeliveryAcknowledgementCodec() {
    }

    static byte[] encode(J7DeliveryAcknowledgement acknowledgement) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("protocolVersion", acknowledgement.protocolVersion());
        root.put("remoteImportId", acknowledgement.remoteImportId().toString());
        root.put("status", acknowledgement.status().name());
        root.put("exportId", acknowledgement.exportId().toString());
        root.put("fileSha256", acknowledgement.fileSha256());
        root.put("dataSha256", acknowledgement.dataSha256());
        root.put("receivedAt", acknowledgement.receivedAt().toString());
        try {
            byte[] encoded = MAPPER.writeValueAsBytes(root);
            if (encoded.length > J7ImportHttpContract.MAXIMUM_ACK_BYTES) {
                throw new IllegalStateException("J7 acknowledgement exceeds its contract bound");
            }
            return encoded;
        }
        catch (JacksonException exception) {
            throw new IllegalStateException("J7 acknowledgement cannot be encoded", exception);
        }
    }
}
