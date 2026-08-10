package com.bettingproject.collection.application;

public final class ReplayFailure extends RuntimeException {

    private final String snapshotSha256;

    public ReplayFailure(String snapshotSha256, Throwable cause) {
        super("Snapshot replay failed; sha256=" + snapshotSha256, cause);
        this.snapshotSha256 = snapshotSha256;
    }

    public String snapshotSha256() {
        return snapshotSha256;
    }
}
