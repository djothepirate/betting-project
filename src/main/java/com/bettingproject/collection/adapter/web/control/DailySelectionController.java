package com.bettingproject.collection.adapter.web.control;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import com.bettingproject.collection.application.control.DailySelectionCommand;
import com.bettingproject.collection.application.control.DailySelectionService;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Profile("control-api")
@RequestMapping("/internal/collection/daily-selection")
public class DailySelectionController {
    static final int MAX_BODY=16_384;
    private static final Set<String> FIELDS=Set.of("windowId","date","calendarCollectionIds","estimatedCallsPerMatch","priorityFixtureIds");
    private final DailySelectionService selections;
    private final JsonMapper mapper=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public DailySelectionController(DailySelectionService selections){this.selections=selections;}
    @PostMapping(value="/preview",consumes="application/json")
    public DailySelectionService.Preview preview(HttpServletRequest request,@RequestParam MultiValueMap<String,String> raw) throws IOException {
        new CollectionQueryParameters(raw,Set.of());
        byte[] body=request.getInputStream().readNBytes(MAX_BODY+1);
        var result=selections.preview(parse(body));
        if(result.code()!=DailySelectionService.Code.OK){
            int status=switch(result.code()){
                case WINDOW_NOT_FOUND,COLLECTION_NOT_FOUND -> 404;
                case INVALID_PRIORITY -> 400;
                default -> 409;
            };
            throw new CollectionWebException(status,result.code().name());
        }
        return result;
    }
    DailySelectionCommand parse(byte[] body){
        if(body.length>MAX_BODY){throw CollectionWebException.invalid("BODY_TOO_LARGE");}
        JsonNode root;
        try{root=mapper.readTree(body);}catch(Exception failure){throw CollectionWebException.invalid("INVALID_JSON");}
        if(root==null || !root.isObject()){throw CollectionWebException.invalid("INVALID_JSON");}
        forbidLocations(root);
        if(root.size()!=FIELDS.size() || !FIELDS.containsAll(root.propertyNames())){throw CollectionWebException.invalid("INVALID_SELECTION");}
        try{
            var cost=root.get("estimatedCallsPerMatch");
            if(!cost.isIntegralNumber() || !cost.canConvertToInt()){throw invalid();}
            return new DailySelectionCommand(uuid(root.get("windowId")),LocalDate.parse(text(root.get("date"))),
                    ids(root.get("calendarCollectionIds"),4),cost.intValue(),Set.copyOf(ids(root.get("priorityFixtureIds"),100)));
        }catch(IllegalArgumentException | java.time.DateTimeException failure){throw invalid();}
    }
    private static List<UUID> ids(JsonNode array,int maximum){
        if(array==null || !array.isArray() || array.size()>maximum){throw invalid();}
        List<UUID> values=new ArrayList<>();array.forEach(node->values.add(uuid(node)));
        if(values.stream().distinct().count()!=values.size()){throw invalid();}return values;
    }
    private static UUID uuid(JsonNode node){
        String text=text(node); UUID id=UUID.fromString(text);
        if(!id.toString().equalsIgnoreCase(text)){throw invalid();}return id;
    }
    private static String text(JsonNode node){if(node==null || !node.isTextual()){throw invalid();}return node.asText();}
    private static void forbidLocations(JsonNode root){
        Deque<JsonNode> pending=new ArrayDeque<>(); pending.add(root);
        while(!pending.isEmpty()){
            JsonNode node=pending.removeFirst();
            if(node.isObject()){
                for(String name:node.propertyNames()){
                    if(Set.of("path","uri","url","file","filepath").contains(name.toLowerCase(Locale.ROOT))){
                        throw CollectionWebException.invalid("ARBITRARY_PATH_FORBIDDEN");
                    }
                }
            }
            if(node.isObject() || node.isArray()){node.forEach(pending::addLast);}
        }
    }
    private static CollectionWebException invalid(){return CollectionWebException.invalid("INVALID_SELECTION");}
}
