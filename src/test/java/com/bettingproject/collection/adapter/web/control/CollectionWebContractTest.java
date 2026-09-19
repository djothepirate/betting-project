package com.bettingproject.collection.adapter.web.control;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import com.bettingproject.shared.application.ReadPage;
import static org.assertj.core.api.Assertions.*;

class CollectionWebContractTest {
    private final CollectionCursorCodec codec=new CollectionCursorCodec();
    private static final String SHA="a".repeat(64);
    private record Row(UUID id,Instant sortTime) implements ReadPage.Timed { }
    @Test void timestampCursorIsUrlSafeAndBoundToScopeAndFiltersNotLimit(){
        var row=new Row(new UUID(0,1),Instant.parse("2030-08-10T12:00:00.123456Z"));
        String cursor=codec.timestamp("windows",SHA,row);
        assertThat(cursor).matches("[A-Za-z0-9_-]+");
        assertThat(codec.timestamp(cursor,"windows",SHA)).isEqualTo(new ReadPage.Anchor(row.sortTime(),row.id()));
        assertThatThrownBy(()->codec.timestamp(cursor,"jobs",SHA)).isInstanceOf(CollectionWebException.class);
        assertThatThrownBy(()->codec.timestamp(cursor,"windows","b".repeat(64))).isInstanceOf(CollectionWebException.class);
        var a=parameters("limit","1"); var b=parameters("limit","100");
        assertThat(a.fingerprint("x")).isEqualTo(b.fingerprint("x"));
    }
    @ParameterizedTest @ValueSource(strings={"", "!", "====", "YWJj", "a", "bad/cursor", "YQ=="})
    void malformedCursorsAreRejected(String value){assertThatThrownBy(()->codec.timestamp(value,"windows",SHA)).isInstanceOf(CollectionWebException.class);}
    @Test void malformedVersionUuidAndInstantAreRejected(){
        for(String value:List.of("collection-v0\nwindows\n"+SHA+"\n2030-01-01T00:00:00Z\n"+new UUID(0,1),
                "collection-v1\nwindows\n"+SHA+"\ninvalid\n"+new UUID(0,1),
                "collection-v1\nwindows\n"+SHA+"\n2030-01-01T00:00:00Z\n1-1-1-1-1")){
            String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(()->codec.timestamp(encoded,"windows",SHA)).isInstanceOf(CollectionWebException.class);
        }
    }
    @Test void capabilityCursorIsBoundToDocumentAndHasAnIntegerAnchor(){
        assertThat(codec.position(codec.position(SHA,3),SHA)).isEqualTo(3);
        assertThatThrownBy(()->codec.position(codec.position(SHA,3),"b".repeat(64))).isInstanceOf(CollectionWebException.class);
    }
    @ParameterizedTest @ValueSource(strings={"0","101","-1","1.0"," 1","01","","2147483648"})
    void invalidPageSizeIsRejected(String value){assertThatThrownBy(()->parameters("limit",value).limit()).isInstanceOf(CollectionWebException.class);}
    @Test void queriesRejectDuplicatesUnknownLocationsAndCoercions(){
        var raw=new LinkedMultiValueMap<String,String>();raw.add("limit","1");raw.add("limit","2");
        assertThatThrownBy(()->new CollectionQueryParameters(raw,Set.of("limit"))).isInstanceOf(CollectionWebException.class);
        assertThatThrownBy(()->parameters("sort","id")).isInstanceOf(CollectionWebException.class);
        assertThatThrownBy(()->parameters("path","C:/private")).isInstanceOf(CollectionWebException.class)
                .extracting("code").isEqualTo("ARBITRARY_PATH_FORBIDDEN");
        assertThatThrownBy(()->parameters("windowId","1-1-1-1-1").uuid("windowId")).isInstanceOf(CollectionWebException.class);
    }
    @Test void readPageUsesAnExtraRowAndImmutableItems(){
        var request=new ReadPage.Request(1,null);assertThat(request.fetchLimit()).isEqualTo(2);
        var page=ReadPage.of(List.of("first","second"),request);
        assertThat(page.items()).containsExactly("first");assertThat(page.hasMore()).isTrue();
        assertThatThrownBy(()->page.items().add("third")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->new ReadPage.Request(0,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void selectionBodyRequiresAnExplicitCostAndExactlyFourCalendars(){
        var parser=new DailySelectionController(null);var value=parser.parse(body());
        assertThat(value.estimatedCallsPerMatch()).isEqualTo(10);assertThat(value.calendarCollectionIds()).hasSize(4);
        assertThat(value.priorityFixtureIds()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"[]","{}","null","{\"path\":\"C:/private\"}",
        "{\"extra\":{\"filePath\":\"C:/private\"}}","{\"x\":1,\"x\":2}","{} {}"})
    void malformedSelectionBodiesAreRejected(String body){assertThatThrownBy(()->new DailySelectionController(null).parse(body.getBytes(StandardCharsets.UTF_8))).isInstanceOf(CollectionWebException.class);}
    @Test void selectionRejectsUnknownFieldsCoercedCostAndLargeBody(){
        var parser=new DailySelectionController(null);String json=new String(body(),StandardCharsets.UTF_8);
        assertThatThrownBy(()->parser.parse(json.replace(":10",":\"10\"").getBytes(StandardCharsets.UTF_8))).isInstanceOf(CollectionWebException.class);
        assertThatThrownBy(()->parser.parse(json.replace("\"date\"","\"operator\":\"synthetic\",\"date\"").getBytes(StandardCharsets.UTF_8))).isInstanceOf(CollectionWebException.class);
        assertThatThrownBy(()->parser.parse(new byte[16_385])).isInstanceOf(CollectionWebException.class);
    }
    @Test void unexpectedErrorsNeverEchoExceptionDetails() throws Exception {
        var registry=new com.bettingproject.collection.application.capability.ProviderCapabilityRegistry(){
            public String registryVersion(){return "synthetic";}
            public String documentSha256(){throw new IllegalStateException("SYNTHETIC_INTERNAL_ERROR_MUST_STAY_PRIVATE");}
            public java.util.Optional<com.bettingproject.collection.domain.capability.ProviderCapability> find(com.bettingproject.collection.domain.capability.ProviderCapabilityKey key){return Optional.empty();}
            public List<com.bettingproject.collection.domain.capability.ProviderCapability> candidates(com.bettingproject.collection.domain.capability.CapabilityRouteKey route){return List.of();}
        };
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new CollectionControlController(null,null,registry,codec))
                .setControllerAdvice(new CollectionProblemHandler()).build();
        String result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/collection/capabilities")
                .param("competitionCode","PPL").param("season","2030").param("phase","LEAGUE").param("dataType","CALENDAR"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("SYNTHETIC_INTERNAL_ERROR_MUST_STAY_PRIVATE","IllegalStateException","stackTrace");
    }
    static byte[] body(){return ("{\"windowId\":\""+new UUID(0,1)+"\",\"date\":\"2030-08-10\",\"estimatedCallsPerMatch\":10,"
        +"\"calendarCollectionIds\":[\""+new UUID(0,2)+"\",\""+new UUID(0,3)+"\",\""+new UUID(0,4)+"\",\""+new UUID(0,5)+"\"],\"priorityFixtureIds\":[]}").getBytes(StandardCharsets.UTF_8);}
    private static CollectionQueryParameters parameters(String key,String value){var raw=new LinkedMultiValueMap<String,String>();raw.add(key,value);return new CollectionQueryParameters(raw,Set.of("limit","cursor","windowId"));}
}
