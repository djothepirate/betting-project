package com.bettingproject.collection.adapter.web.control;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import com.bettingproject.collection.domain.SnapshotHasher;
import org.springframework.util.MultiValueMap;

final class CollectionQueryParameters {
    private final Map<String,String> values = new TreeMap<>();
    CollectionQueryParameters(MultiValueMap<String,String> raw, Set<String> allowed) {
        if (raw.size()>20) { throw invalid(); }
        raw.forEach((name,list) -> {
            if (Set.of("path","uri","url","file","filepath").contains(name.toLowerCase(java.util.Locale.ROOT))) {
                throw CollectionWebException.invalid("ARBITRARY_PATH_FORBIDDEN");
            }
            if (!allowed.contains(name) || list.size()!=1 || list.getFirst()==null || list.getFirst().length()>2048) { throw invalid(); }
            values.put(name,list.getFirst());
        });
    }
    String text(String name,int max) {
        String value=values.get(name);
        if (value!=null && (value.isBlank() || value.length()>max || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl))) { throw invalid(); }
        return value;
    }
    String required(String name,int max) { String value=text(name,max); if(value==null){throw invalid();} return value; }
    int limit() {
        String value=values.get("limit");
        if(value==null){return 50;}
        if(!value.matches("[1-9][0-9]{0,2}")){throw invalid();}
        int limit=Integer.parseInt(value); if(limit>100){throw invalid();} return limit;
    }
    String cursor() { return text("cursor",2048); }
    UUID uuid(String name) {
        String value=text(name,36); if(value==null){return null;}
        return parseUuid(value);
    }
    static UUID parseUuid(String value) {
        if(value==null || value.length()!=36){throw invalid();}
        try { UUID id=UUID.fromString(value); if(!id.toString().equalsIgnoreCase(value)){throw invalid();} return id; }
        catch(IllegalArgumentException failure){throw invalid();}
    }
    LocalDate date(String name) {
        String value=text(name,10); if(value==null){return null;}
        try { LocalDate date=LocalDate.parse(value); if(date.getYear()<1 || date.getYear()>9999){throw invalid();} return date; }
        catch(java.time.DateTimeException failure){throw invalid();}
    }
    <E extends Enum<E>> E enumeration(String name,Class<E> type) {
        String value=text(name,64); if(value==null){return null;}
        try{return Enum.valueOf(type,value);}catch(IllegalArgumentException failure){throw invalid();}
    }
    String fingerprint(String context) {
        StringBuilder canonical=new StringBuilder("collection-query-v1:").append(context.length()).append(':').append(context);
        values.forEach((key,value)->{
            if(!key.equals("limit") && !key.equals("cursor")) {
                canonical.append('|').append(key.length()).append(':').append(key).append('|').append(value.length()).append(':').append(value);
            }
        });
        return SnapshotHasher.sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static CollectionWebException invalid(){return CollectionWebException.invalid("INVALID_QUERY");}
}
