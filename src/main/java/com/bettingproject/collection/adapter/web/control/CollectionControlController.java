package com.bettingproject.collection.adapter.web.control;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.bettingproject.collection.application.budget.ProviderBudgetService;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.application.control.CollectionQueryPort.*;
import com.bettingproject.collection.application.control.CollectionQueryService;
import com.bettingproject.collection.domain.budget.BudgetModel;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.operations.application.jobs.JobQueryPort;
import com.bettingproject.operations.domain.JobModel;
import com.bettingproject.shared.application.ReadPage;
import org.springframework.context.annotation.Profile;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("control-api")
@RequestMapping("/internal/collection")
public class CollectionControlController {
    private static final Set<String> PAGE=Set.of("limit","cursor");
    private final CollectionQueryService queries;
    private final ProviderBudgetService budget;
    private final ProviderCapabilityRegistry registry;
    private final CollectionCursorCodec cursors;
    public CollectionControlController(CollectionQueryService queries,ProviderBudgetService budget,
            ProviderCapabilityRegistry registry,CollectionCursorCodec cursors){
        this.queries=queries; this.budget=budget; this.registry=registry; this.cursors=cursors;
    }
    public record Page<T>(List<T> items,String nextCursor){}
    public record CapabilityView(ProviderCapabilityKey key,CapabilityRouteKey route,CapabilityStatus status,
            CapabilityAuthorityRole authorityRole,boolean enabled,boolean operational,int evidenceCount){}
    public record Capabilities(String registryVersion,String documentSha256,List<CapabilityView> items,String nextCursor){}
    private enum CalendarState { RUNNING,COMPLETED,INCOMPLETE }

    @GetMapping("/capabilities")
    public Capabilities capabilities(@RequestParam MultiValueMap<String,String> raw){
        var p=parameters(raw,Set.of("competitionCode","season","phase","dataType","limit","cursor"));
        var type=p.enumeration("dataType",CapabilityDataType.class); if(type==null){throw CollectionWebException.invalid("INVALID_QUERY");}
        var route=new CapabilityRouteKey(p.required("competitionCode",64),p.required("season",64),p.required("phase",64),type);
        String hash=p.fingerprint(registry.documentSha256()); int start=cursors.position(p.cursor(),hash);
        var all=registry.candidates(route); if(start>all.size()){throw CollectionWebException.invalid("INVALID_CURSOR");}
        int end=Math.min(all.size(),start+p.limit());
        var views=all.subList(start,end).stream().map(c->new CapabilityView(c.key(),c.route(),c.status(),c.authorityRole(),c.enabled(),c.operational(),c.evidence().size())).toList();
        return new Capabilities(registry.registryVersion(),registry.documentSha256(),views,end<all.size()?cursors.position(hash,end):null);
    }
    @GetMapping("/budget-windows")
    public Page<WindowView> windows(@RequestParam MultiValueMap<String,String> raw){
        var p=parameters(raw,Set.of("provider","status","limit","cursor"));
        var state=p.enumeration("status",BudgetModel.WindowState.class);
        var filter=new Filter(p.text("provider",64),state==null?null:state.name(),null,null);
        return page(queries.windows(filter,request(p,"windows","")),p,"windows","");
    }
    @GetMapping("/budget-windows/{id}")
    public WindowView window(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        parameters(raw,Set.of()); return requireWindow(id);
    }
    @GetMapping("/budget-windows/{id}/availability")
    public BudgetModel.Availability availability(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        parameters(raw,Set.of()); requireWindow(id); return budget.availability(id);
    }
    @GetMapping("/budget-windows/{id}/intents")
    public Page<IntentView> intents(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        var p=parameters(raw,Set.of("status","limit","cursor")); requireWindow(id);
        var state=p.enumeration("status",BudgetModel.IntentState.class);
        return page(queries.intents(id,state==null?null:state.name(),request(p,"intents",id.toString())),p,"intents",id.toString());
    }
    @GetMapping("/budget-windows/{id}/events")
    public Page<BudgetEventView> events(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        var p=parameters(raw,PAGE); requireWindow(id);
        return page(queries.budgetEvents(id,request(p,"budget-events",id.toString())),p,"budget-events",id.toString());
    }
    @GetMapping("/incidents")
    public Page<IncidentView> incidents(@RequestParam MultiValueMap<String,String> raw){
        var p=parameters(raw,Set.of("provider","windowId","code","limit","cursor"));
        var filter=new Filter(p.text("provider",64),null,p.uuid("windowId"),p.text("code",64));
        return page(queries.incidents(filter,request(p,"incidents","")),p,"incidents","");
    }
    @GetMapping("/jobs")
    public Page<JobQueryPort.JobView> jobs(@RequestParam MultiValueMap<String,String> raw){
        var p=parameters(raw,Set.of("type","status","limit","cursor"));
        return page(queries.jobs(p.enumeration("type",JobModel.Type.class),p.enumeration("status",JobModel.Status.class),request(p,"jobs","")),p,"jobs","");
    }
    @GetMapping("/jobs/{id}")
    public JobQueryPort.JobView job(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        parameters(raw,Set.of());return queries.job(id).orElseThrow(CollectionWebException::missing);
    }
    @GetMapping("/jobs/{id}/events")
    public Page<JobQueryPort.EventView> jobEvents(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        var p=parameters(raw,PAGE); queries.job(id).orElseThrow(CollectionWebException::missing);
        return page(queries.jobEvents(id,request(p,"job-events",id.toString())),p,"job-events",id.toString());
    }
    @GetMapping("/calendars")
    public Page<CalendarView> calendars(@RequestParam MultiValueMap<String,String> raw){
        var p=parameters(raw,Set.of("provider","windowId","status","date","limit","cursor"));
        var state=p.enumeration("status",CalendarState.class);
        var filter=new Filter(p.text("provider",64),state==null?null:state.name(),p.uuid("windowId"),null);
        return page(queries.calendars(filter,p.date("date"),request(p,"calendars","")),p,"calendars","");
    }
    @GetMapping("/calendars/{id}")
    public CalendarView calendar(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        parameters(raw,Set.of());return queries.calendar(id).orElseThrow(CollectionWebException::missing);
    }
    @GetMapping("/calendars/{id}/pages")
    public Page<PageView> pages(@PathVariable("id") String idText,@RequestParam MultiValueMap<String,String> raw){
        UUID id=CollectionQueryParameters.parseUuid(idText);
        var p=parameters(raw,PAGE);queries.calendar(id).orElseThrow(CollectionWebException::missing);
        return page(queries.pages(id,request(p,"calendar-pages",id.toString())),p,"calendar-pages",id.toString());
    }
    private WindowView requireWindow(UUID id){return queries.window(id).orElseThrow(CollectionWebException::missing);}
    private CollectionQueryParameters parameters(MultiValueMap<String,String> values,Set<String> allowed){return new CollectionQueryParameters(values,allowed);}
    private ReadPage.Request request(CollectionQueryParameters p,String scope,String context){
        return new ReadPage.Request(p.limit(),cursors.timestamp(p.cursor(),scope,p.fingerprint(context)));
    }
    private <T extends ReadPage.Timed> Page<T> page(ReadPage<T> page,CollectionQueryParameters p,String scope,String context){
        return new Page<>(page.items(),page.hasMore()?cursors.timestamp(scope,p.fingerprint(context),page.items().getLast()):null);
    }
}
