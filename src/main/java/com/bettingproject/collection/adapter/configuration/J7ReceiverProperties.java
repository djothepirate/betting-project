package com.bettingproject.collection.adapter.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("betting.integration.j7-receiver")
public class J7ReceiverProperties {

    public static final long CONTRACT_MAX_REQUEST_BYTES = 5_242_880L;
    public static final long CONTRACT_MAX_ACK_BYTES = 16_384L;

    private boolean enabled;
    private int retentionDays = 30;
    private long maxRequestBytes = CONTRACT_MAX_REQUEST_BYTES;
    private long maxAckBytes = CONTRACT_MAX_ACK_BYTES;
    private List<String> clientCertificateSha256Allowlist = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public long getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(long maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public long getMaxAckBytes() {
        return maxAckBytes;
    }

    public void setMaxAckBytes(long maxAckBytes) {
        this.maxAckBytes = maxAckBytes;
    }

    public List<String> getClientCertificateSha256Allowlist() {
        return List.copyOf(clientCertificateSha256Allowlist);
    }

    public void setClientCertificateSha256Allowlist(
            List<String> clientCertificateSha256Allowlist) {
        this.clientCertificateSha256Allowlist = clientCertificateSha256Allowlist == null
                ? new ArrayList<>()
                : new ArrayList<>(clientCertificateSha256Allowlist);
    }
}
