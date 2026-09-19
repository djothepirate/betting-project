package com.bettingproject.collection.adapter.web.control;

public class CollectionWebException extends RuntimeException {
    private final int status;
    private final String code;
    public CollectionWebException(int status, String code) { super(code); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    static CollectionWebException invalid(String code) { return new CollectionWebException(400,code); }
    static CollectionWebException missing() { return new CollectionWebException(404,"RESOURCE_NOT_FOUND"); }
}
