package com.bettingproject.collection.application.control;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.bettingproject.BettingProjectApplication;
import com.bettingproject.catalog.application.CatalogCommandService;
import com.bettingproject.catalog.domain.CompetitionType;
import com.bettingproject.collection.adapter.web.control.*;
import com.bettingproject.collection.application.budget.*;
import com.bettingproject.collection.application.calendar.*;
import com.bettingproject.collection.application.capability.ProviderCapabilityRegistry;
import com.bettingproject.collection.domain.budget.BudgetModel.*;
import com.bettingproject.collection.domain.capability.*;
import com.bettingproject.identity.application.ProviderMappingRepository;
import com.bettingproject.identity.domain.*;
import com.bettingproject.operations.application.jobs.JobWorker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real services, PostgreSQL and job dispatch; HTTP is MockMvc and providers are in-memory fakes. */
@Testcontainers
class CollectionControlIT {
    static final Instant NOW=Instant.parse("2030-08-10T12:00:00Z");
    static final LocalDate DAY=LocalDate.parse("2030-08-10");
    static final String SHA="c".repeat(64);
    static final String ROOT="/internal/collection";
    static final String[] CODES={"PPL","PD","DED","ELC"};
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17-alpine");
    static ConfigurableApplicationContext control,batch;
    static JdbcClient jdbc;static MockMvc mvc;
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final AtomicInteger calls=new AtomicInteger();
    static int responseStatus=200;static boolean registryActive=true;

    @BeforeAll static void start(){
        control=open("control-api");batch=open("batch-worker");jdbc=control.getBean(JdbcClient.class);
        mvc=MockMvcBuilders.standaloneSetup(control.getBean(CollectionControlController.class),control.getBean(DailySelectionController.class))
                .setControllerAdvice(control.getBean(CollectionProblemHandler.class)).build();
    }
    @AfterAll static void stop(){batch.close();control.close();}
    @BeforeEach void reset(){
        jdbc.sql("TRUNCATE persistent_job, provider_budget_scope, raw_snapshot, canonical_competition, canonical_team, provider_mapping, control_command_receipt, provider_call_audit, outbox_message CASCADE").update();
        calls.set(0);responseStatus=200;registryActive=true;
    }

    @Test void completeOfflineChainSelectsSevenWithRealBudgetAndNoNewIntent() throws Exception {
        UUID window=window("highlightly");List<UUID> calendars=collectCore(window);Map<String,Long> before=counts();
        var result=preview(window,calendars,10,List.of(),200);
        assertThat(result.path("code").asText()).isEqualTo("OK");
        assertThat(result.path("budget").path("available").asLong()).isEqualTo(76);
        assertThat(result.path("selection").path("selected").size()).isEqualTo(7);
        assertThat(result.path("selection").path("estimatedCalls").asLong()).isEqualTo(70);
        assertThat(result.path("reservationCreated").asBoolean()).isFalse();
        assertThat(preview(window,calendars,10,List.of(),200)).isEqualTo(result);
        assertThat(counts()).isEqualTo(before);assertThat(calls.get()).isEqualTo(4);
        var affordable=preview(window,calendars,11,List.of(),200);
        assertThat(affordable.path("selection").path("selected").size()).isEqualTo(6);
    }
    @Test void explicitPriorityIsAppliedOnlyToVerifiedCandidates() throws Exception {
        UUID window=window("highlightly");var calendars=collectCore(window);
        UUID priority=jdbc.sql("SELECT id FROM canonical_fixture ORDER BY kickoff_at DESC, id DESC LIMIT 1").query(UUID.class).single();
        var result=preview(window,calendars,10,List.of(priority),200);
        assertThat(result.path("selection").path("selected").get(0).path("id").asText()).isEqualTo(priority.toString());
        assertThat(preview(window,calendars,10,List.of(UUID.randomUUID()),400).path("code").asText()).isEqualTo("INVALID_PRIORITY");
    }
    @Test void allReadRoutesExposeSafeProjectionsAndKeepTheirParentScope() throws Exception {
        UUID window=window("highlightly");var calendars=collectCore(window);UUID other=window("football-data.org");
        String first=calendars.getFirst().toString();
        List<String> paths=List.of("/budget-windows","/budget-windows/"+window,"/budget-windows/"+window+"/availability",
            "/budget-windows/"+window+"/intents","/budget-windows/"+window+"/events","/incidents","/jobs",
            "/jobs/"+first,"/jobs/"+first+"/events","/calendars","/calendars/"+first,"/calendars/"+first+"/pages");
        for(String path:paths){
            String body=mvc.perform(get(ROOT+path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("payload\"","payloadJson","accountRef","idempotencyKey","claimToken","leaseToken","synthetic-operator","requestSha256","lastErrorMessage");
        }
        assertThat(read("/budget-windows/"+other+"/intents").path("items").size()).isZero();
        assertThat(read("/calendars/"+first+"/pages").path("items").size()).isEqualTo(1);
        var page=read("/calendars/"+first+"/pages").path("items").get(0);
        assertThat(page.path("rawSnapshotId").asText()).isNotEmpty();assertThat(page.path("rawSha256").asText()).hasSize(64);
    }
    @Test void timestampTiesUseUuidKeysetWithoutGapsAndCursorRejectsChangedScope() throws Exception {
        for(int i=0;i<5;i++){window("highlightly");}
        List<String> all=new ArrayList<>();String cursor=null;
        do{
            var request=get(ROOT+"/budget-windows").param("limit","2").param("provider","highlightly");
            if(cursor!=null){request.param("cursor",cursor);}
            var node=json(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            node.path("items").forEach(item->all.add(item.path("id").asText()));
            cursor=node.path("nextCursor").isNull()?null:node.path("nextCursor").asText();
        }while(cursor!=null);
        assertThat(all).hasSize(5).doesNotHaveDuplicates();
        assertThat(all).isEqualTo(all.stream().sorted(Comparator.reverseOrder()).toList());
        String saved=read("/budget-windows?limit=1&provider=highlightly").path("nextCursor").asText();
        mvc.perform(get(ROOT+"/jobs").param("cursor",saved)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        mvc.perform(get(ROOT+"/budget-windows").param("cursor",saved).param("provider","football-data.org")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT+"/budget-windows").param("cursor",saved).param("provider","highlightly").param("limit","3")).andExpect(status().isOk());
    }
    @Test void filtersAreBoundAndCalendarJobFiltersRemainExact() throws Exception {
        UUID window=window("highlightly");collectCore(window);window("football-data.org");
        assertThat(read("/budget-windows?provider=highlightly&status=ACTIVE").path("items").size()).isEqualTo(1);
        assertThat(read("/calendars?windowId="+window+"&provider=highlightly&status=COMPLETED&date=2030-08-10").path("items").size()).isEqualTo(4);
        assertThat(read("/calendars?date=2030-08-11").path("items").size()).isZero();
        assertThat(read("/jobs?type=CALENDAR_DISCOVERY&status=SUCCEEDED").path("items").size()).isEqualTo(4);
        assertThat(read("/jobs?type=REPLAY_NORMALIZATION").path("items").size()).isZero();
        assertThat(read("/budget-windows/"+window+"/intents?status=RESULT_RECORDED").path("items").size()).isEqualTo(4);
        var injection=mvc.perform(get(ROOT+"/budget-windows").param("provider","' OR 1=1 --"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json(injection).path("items").size()).isZero();assertThat(count("provider_budget_window")).isEqualTo(2);
    }
    @Test void keysetPagesForJobsCalendarsIntentsAndEventsStayBoundToTheirParent() throws Exception {
        UUID window=window("highlightly");var calendars=collectCore(window);UUID other=window("football-data.org");
        for(String path:List.of("/jobs","/calendars","/budget-windows/"+window+"/intents")){
            List<String> ids=readAllIds(path);assertThat(ids).hasSize(4).doesNotHaveDuplicates();
        }
        List<String> events=readAllIds("/budget-windows/"+window+"/events");
        assertThat(events.size()).isGreaterThan(4);assertThat(events).doesNotHaveDuplicates();
        var page=read("/budget-windows/"+window+"/events?limit=1");
        mvc.perform(get(ROOT+"/budget-windows/"+other+"/events").param("cursor",page.path("nextCursor").asText()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        assertThat(readAllIds("/jobs/"+calendars.getFirst()+"/events")).doesNotHaveDuplicates().isNotEmpty();
    }
    @ParameterizedTest @ValueSource(ints={401,403,429})
    void supplierSuspensionIsVisibleAndDoesNotBlockTheOtherProvider(int httpStatus) throws Exception {
        UUID window=window("highlightly");UUID other=window("football-data.org");responseStatus=httpStatus;
        enqueue(window,0);assertThat(batch.getBean(JobWorker.class).tick()).isTrue();
        assertThat(read("/budget-windows/"+window).path("state").asText()).isEqualTo("SUSPENDED");
        var incidents=read("/incidents?provider=highlightly&windowId="+window).path("items");assertThat(incidents.size()).isEqualTo(1);
        String code=incidents.get(0).path("code").asText();assertThat(code).contains(Integer.toString(httpStatus));
        assertThat(read("/incidents?code="+code).path("items").size()).isEqualTo(1);
        assertThat(read("/incidents?provider=football-data.org").path("items").size()).isZero();
        assertThat(read("/budget-windows/"+other+"/availability").path("code").asText()).isEqualTo("OK");
        assertThat(count("provider_call_audit")).isEqualTo(1);assertThat(count("provider_call_intent")).isEqualTo(1);
    }
    @Test void incompleteCalendarCannotBePresentedAsADailySelection() throws Exception {
        UUID window=window("highlightly");var calendars=collectCore(window);
        jdbc.sql("UPDATE calendar_collection SET status='INCOMPLETE', reason_code='SYNTHETIC_INCOMPLETE' WHERE id=:id").param("id",calendars.getFirst()).update();
        assertThat(preview(window,calendars,10,List.of(),409).path("code").asText()).isEqualTo("CALENDAR_NOT_READY");
    }
    @Test void inactiveRegistryNeverGrantsSelectionFromHistoricalData() throws Exception {
        UUID window=window("highlightly");var calendars=collectCore(window);registryActive=false;
        assertThat(preview(window,calendars,10,List.of(),409).path("code").asText()).isEqualTo("CALENDAR_NOT_READY");
    }
    @Test void reservationsAndSuspensionAreAccountedForAtPreviewTime() throws Exception {
        UUID window=window("highlightly");var calendars=collectCore(window);var budget=control.getBean(ProviderBudgetService.class);
        for(int i=0;i<70;i++){assertThat(budget.reserve(new BudgetCommands.Reserve(window,"held-"+i,"SYNTHETIC_OPTION",SHA)).code()).isEqualTo(ResultCode.OK);}
        var result=preview(window,calendars,10,List.of(),200);assertThat(result.path("budget").path("available").asLong()).isEqualTo(6);
        assertThat(result.path("selection").path("selected").size()).isZero();assertThat(count("provider_call_intent")).isEqualTo(74);
    }
    @Test void missingParentIs404ButEmptyExistingHistoryIs200() throws Exception {
        UUID window=window("highlightly");mvc.perform(get(ROOT+"/budget-windows/"+window+"/intents")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        for(String path:List.of("/budget-windows/","/budget-windows/%s/intents","/jobs/%s/events","/calendars/%s/pages")){
            String target=path.contains("%s")?path.formatted(UUID.randomUUID()):path+UUID.randomUUID();
            mvc.perform(get(ROOT+target)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }
    @Test void capabilitiesAreExactBoundedAndUnknownRoutesStayEmpty() throws Exception {
        var response=json(mvc.perform(get(ROOT+"/capabilities").param("competitionCode","PPL").param("season","2030/2031")
            .param("phase","LEAGUE").param("dataType","CALENDAR").param("limit","1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(response.path("items").size()).isEqualTo(1);assertThat(response.path("documentSha256").asText()).isEqualTo(SHA);
        assertThat(response.toString()).doesNotContain("synthetic-evidence");
        assertThat(read("/capabilities?competitionCode=PPL&season=2031&phase=LEAGUE&dataType=CALENDAR").path("items").size()).isZero();
        assertThat(read("/capabilities?competitionCode=*&season=2030&phase=LEAGUE&dataType=CALENDAR").path("items").size()).isZero();
    }
    @Test void invalidQueriesAndBodiesProduceGenericProblemDetails() throws Exception {
        mvc.perform(get(ROOT+"/jobs").param("path","C:/private/sensitive"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARBITRARY_PATH_FORBIDDEN"))
            .andExpect(jsonPath("$.instance").value(ROOT)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
        mvc.perform(get(ROOT+"/jobs").param("limit","1","2")).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT+"/jobs").param("sort","sql")).andExpect(status().isBadRequest());
        for(String path:List.of("/jobs/1-1-1-1-1","/budget-windows/1-1-1-1-1/availability","/calendars/1-1-1-1-1/pages")){
            mvc.perform(get(ROOT+path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        }
        mvc.perform(post(ROOT+"/daily-selection/preview").contentType(MediaType.APPLICATION_JSON).content("{\"nested\":{\"uri\":\"private\"}}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARBITRARY_PATH_FORBIDDEN"));
        mvc.perform(post(ROOT+"/daily-selection/preview").contentType(MediaType.APPLICATION_JSON).content(new byte[16_385]))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BODY_TOO_LARGE"));
        assertThat(count("provider_budget_window")).isZero();
    }

    private List<UUID> collectCore(UUID window){
        bind();List<UUID> result=new ArrayList<>();for(int i=0;i<4;i++){result.add(enqueue(window,i));}
        for(int i=0;i<4;i++){assertThat(batch.getBean(JobWorker.class).tick()).isTrue();}
        assertThat(batch.getBean(JobWorker.class).tick()).isFalse();return result;
    }
    private UUID enqueue(UUID window,int index){
        return control.getBean(CalendarJobPlanningService.class).enqueueDiscovery("synthetic-job-"+UUID.randomUUID(),
            new CalendarCollectionCommand(UUID.randomUUID(),window,key(index),DAY,2030),Instant.parse("2020-01-01T00:00:00Z"),3).job().id();
    }
    private static UUID window(String provider){
        UUID id=UUID.randomUUID();boolean hl=provider.equals("highlightly");
        var command=new BudgetCommands.Initialize(id,provider,"synthetic-scope-"+UUID.randomUUID(),NOW.minusSeconds(3600),NOW.plusSeconds(86400),
            hl?100L:null,hl?80:20,hl?20:0,0,0,hl?null:10,hl?null:Duration.ofMinutes(1),hl?100L:null,
            hl?NOW.minusSeconds(1):null,hl?NOW.plusSeconds(86400):null,new Proof("synthetic-evidence",SHA),"Synthetic initialization");
        assertThat(control.getBean(ProviderBudgetAdministration.class).initialize(command).code()).isEqualTo(ResultCode.OK);return id;
    }
    private void bind(){
        var catalog=control.getBean(CatalogCommandService.class);var mappings=control.getBean(ProviderMappingRepository.class);
        for(int i=0;i<4;i++){
            UUID competition=catalog.registerCompetition("Synthetic "+CODES[i],"ZZZ",CompetitionType.DOMESTIC_LEAGUE);
            var key=key(i);mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(key.provider(),ProviderEntityType.COMPETITION,key.providerCompetitionId(),competition,key.sourceSeason(),key.sourcePhase(),NOW));
            for(int t=0;t<2;t++){
                String ref=Integer.toString(910001+i*2+t);UUID team=catalog.registerTeam("Synthetic Team "+ref,"ZZZ");
                mappings.insertIfAbsentAndResolve(ProviderMapping.confirmed(key.provider(),ProviderEntityType.TEAM,ref,team,"","",NOW));
            }
        }
    }
    private JsonNode preview(UUID window,List<UUID> calendars,int cost,List<UUID> priorities,int expected) throws Exception {
        byte[] body=JSON.writeValueAsBytes(Map.of("windowId",window.toString(),"date",DAY.toString(),"calendarCollectionIds",calendars.stream().map(UUID::toString).toList(),
            "estimatedCallsPerMatch",cost,"priorityFixtureIds",priorities.stream().map(UUID::toString).toList()));
        return json(mvc.perform(post(ROOT+"/daily-selection/preview").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private static JsonNode read(String path) throws Exception {return json(mvc.perform(get(ROOT+path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private static List<String> readAllIds(String path) throws Exception {
        List<String> ids=new ArrayList<>();String cursor=null;int pages=0;
        do {
            var request=get(ROOT+path).param("limit","2");if(cursor!=null){request.param("cursor",cursor);}
            var result=json(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            result.path("items").forEach(item->ids.add(item.path("id").asText()));
            cursor=result.path("nextCursor").isNull()?null:result.path("nextCursor").asText();
            assertThat(++pages).isLessThan(50);
        }while(cursor!=null);return ids;
    }
    private static JsonNode json(String body){return JSON.readTree(body);}
    private static long count(String table){return jdbc.sql("SELECT count(*) FROM "+table).query(Long.class).single();}
    private static Map<String,Long> counts(){var counts=new TreeMap<String,Long>();for(String table:List.of("provider_call_intent","provider_budget_event","persistent_job","outbox_message","raw_snapshot","fixture_observation","fixture_application_log")){counts.put(table,count(table));}return counts;}
    private static ProviderCapabilityKey key(int i){return new ProviderCapabilityKey("highlightly",Integer.toString(920001+i),"2030","Regular Season - 1",CapabilityDataType.CALENDAR);}
    private static List<ProviderCapability> entries(){
        var result=new ArrayList<ProviderCapability>();for(int i=0;i<4;i++){result.add(new ProviderCapability(key(i),new CapabilityRouteKey(CODES[i],"2030/2031","LEAGUE",CapabilityDataType.CALENDAR),
            CapabilityStatus.PRIMARY,CapabilityAuthorityRole.PRIMARY,registryActive,List.of(new CapabilityEvidenceReference("synthetic-evidence",NOW,SHA))));}return result;
    }
    private static byte[] body(CalendarPageRequest request){
        int i=Integer.parseInt(request.capability().providerCompetitionId())-920001;int total=i<2?5:2;var rows=new ArrayList<String>();
        for(int n=0;n<total;n++){rows.add("""
            {"id":%d,"round":"Regular Season - 1","date":"2030-08-10T19:%02d:00Z",
             "homeTeam":{"id":%d,"name":"Synthetic Home"},"awayTeam":{"id":%d,"name":"Synthetic Away"},
             "league":{"id":%s,"season":2030,"name":"Synthetic League"},"state":{"description":"Not started"}}
            """.formatted(900001+i*100+n,n,910001+i*2,910002+i*2,request.capability().providerCompetitionId()));}
        return ("{\"data\":["+String.join(",",rows)+"],\"pagination\":{\"totalCount\":"+total+",\"offset\":0,\"limit\":100}}").getBytes(StandardCharsets.UTF_8);
    }
    private static ConfigurableApplicationContext open(String profile){return new SpringApplicationBuilder(BettingProjectApplication.class,Synthetic.class)
        .profiles(profile).web(WebApplicationType.NONE).properties("spring.main.banner-mode=off")
        .run("--spring.datasource.url="+DB.getJdbcUrl(),"--spring.datasource.username="+DB.getUsername(),"--spring.datasource.password="+DB.getPassword(),
            "--BETTING_DB_PASSWORD=TEST_ONLY_PLACEHOLDER","--betting.operator.id=synthetic-operator","--betting.collection.worker.enabled=false");}
    @TestConfiguration(proxyBeanMethods=false)
    static class Synthetic {
        @Bean @Primary Clock testClock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean @Primary ProviderCapabilityRegistry registry(){return new ProviderCapabilityRegistry(){
            public String registryVersion(){return "synthetic-control-v1";}public String documentSha256(){return SHA;}
            public Optional<ProviderCapability> find(ProviderCapabilityKey key){return entries().stream().filter(c->c.key().equals(key)).findFirst();}
            public List<ProviderCapability> candidates(CapabilityRouteKey route){return entries().stream().filter(c->c.route().equals(route)).toList();}
        };}
        @Bean @Order(-100) CalendarPageClient fakeClient(){return new CalendarPageClient(){
            public String provider(){return "highlightly";}public boolean available(){return true;}
            public CalendarPageResponse fetch(CalendarPageRequest request){
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();calls.incrementAndGet();
                return new CalendarPageResponse(NOW,NOW,responseStatus,responseStatus==200?body(request):"{}".getBytes(StandardCharsets.UTF_8),null,null);
            }
        };}
    }
}
