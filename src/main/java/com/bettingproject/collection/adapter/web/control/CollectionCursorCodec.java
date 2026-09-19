package com.bettingproject.collection.adapter.web.control;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import com.bettingproject.shared.application.ReadPage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("control-api")
public class CollectionCursorCodec {
    String timestamp(String scope,String hash,ReadPage.Timed row) {
        return encode(scope,hash,row.sortTime().toString(),row.id().toString());
    }
    ReadPage.Anchor timestamp(String value,String scope,String hash) {
        if(value==null){return null;}
        String[] fields=decode(value,scope,hash);
        try {
            UUID id=UUID.fromString(fields[4]);
            if(!id.toString().equals(fields[4])){throw invalid();}
            return new ReadPage.Anchor(Instant.parse(fields[3]),id);
        } catch(IllegalArgumentException|java.time.DateTimeException failure){throw invalid();}
    }
    String position(String hash,int index){return encode("capabilities",hash,Integer.toString(index),"index");}
    int position(String value,String hash){
        if(value==null){return 0;}
        String[] fields=decode(value,"capabilities",hash);
        if(!fields[4].equals("index") || !fields[3].matches("[0-9]{1,9}")){throw invalid();}
        return Integer.parseInt(fields[3]);
    }
    private String encode(String scope,String hash,String time,String id){
        return Base64.getUrlEncoder().withoutPadding().encodeToString(String.join("\n","collection-v1",scope,hash,time,id).getBytes(StandardCharsets.UTF_8));
    }
    private String[] decode(String value,String scope,String hash){
        if(value.isEmpty() || value.length()>2048 || !value.matches("[A-Za-z0-9_-]+")){throw invalid();}
        try {
            byte[] bytes=Base64.getUrlDecoder().decode(value);
            if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value)){throw invalid();}
            String[] fields=new String(bytes,StandardCharsets.UTF_8).split("\n",-1);
            if(fields.length!=5 || !fields[0].equals("collection-v1") || !fields[1].equals(scope) || !fields[2].equals(hash)){throw invalid();}
            return fields;
        }catch(IllegalArgumentException failure){throw invalid();}
    }
    private CollectionWebException invalid(){return CollectionWebException.invalid("INVALID_CURSOR");}
}
