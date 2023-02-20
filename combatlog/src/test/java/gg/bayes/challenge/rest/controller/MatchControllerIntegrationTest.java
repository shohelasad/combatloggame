package gg.bayes.challenge.rest.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.bayes.challenge.persistence.model.CombatLogEntryEntity;
import gg.bayes.challenge.persistence.model.MatchEntity;
import gg.bayes.challenge.persistence.repository.MatchRepository;
import gg.bayes.challenge.rest.model.HeroDamage;
import gg.bayes.challenge.rest.model.HeroItem;
import gg.bayes.challenge.rest.model.HeroKills;
import gg.bayes.challenge.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Integration test template to get you started. Add tests and make modifications as you see fit.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MatchControllerIntegrationTest {

    private static final String COMBATLOG_FILE_1 = "/data/combatlog_1.log.txt";
    private static final String COMBATLOG_FILE_2 = "/data/combatlog_2.log.txt";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MatchService matchService;

    private Map<String, Long> matchIds;

    @BeforeAll
    void setup() throws Exception {
        // Populate the database with all events from both sample data files and store the returned
        // match IDS.
        matchIds = Map.of(
                COMBATLOG_FILE_1, ingestMatch(COMBATLOG_FILE_1),
                COMBATLOG_FILE_2, ingestMatch(COMBATLOG_FILE_2));
    }

    // TODO: add your tests
    // Replace this test method with the tests that you consider appropriate to test your implementation.

    @Test
    public void testGetMatch() throws Exception {
        // create a test match with some data
        Long matchId = 12345L;
        MatchEntity match = new MatchEntity();
        match.setId(matchId);
        Set<CombatLogEntryEntity> entries = new HashSet<>();
        CombatLogEntryEntity entry = new CombatLogEntryEntity();
        entry.setId(1l);
        entry.setActor("Hero1");
        entry.setTarget("TestTarget");
        entry.setType(CombatLogEntryEntity.Type.HERO_KILLED);
        entries.add(entry);
        entry = new CombatLogEntryEntity();
        entry.setId(2l);
        entry.setActor("Hero2");
        entry.setTarget("TestTarget");
        entry.setType(CombatLogEntryEntity.Type.HERO_KILLED);
        entries.add(entry);
        entry = new CombatLogEntryEntity();
        entry.setId(3l);
        entry.setActor("Hero1");
        entry.setTarget("TestTarget");
        entry.setType(CombatLogEntryEntity.Type.HERO_KILLED);
        entries.add(entry);
        match.setCombatLogEntries(entries);
        HeroKills heroKills = new HeroKills("TestHero", 10);
        List<HeroKills> expected = new ArrayList<>();
        expected.add(heroKills);

        // mock the MatchService to return the test match data
        when(matchService.findById(matchId)).thenReturn(Optional.of(match));

        // perform the request
        MvcResult result = mvc.perform(get("/api/match/{matchId}", matchId))
                .andExpect(status().isOk())
                .andReturn();
        List<HeroKills> resultHeroKills = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {});
        assertThat(resultHeroKills.size()).isEqualTo(2);
        assertThat(resultHeroKills.get(0).getHero()).isEqualTo("Hero1");
        assertThat(resultHeroKills.get(0).getKills()).isEqualTo(2);
        assertThat(resultHeroKills.get(1).getHero()).isEqualTo("Hero2");
        assertThat(resultHeroKills.get(1).getKills()).isEqualTo(1);

    }

    @Test
    void getHeroItems() throws Exception {
        Long matchId = 1L;
        String heroName = "hero1";

        // Mock the MatchService to return a match entity
        Set<CombatLogEntryEntity> entries = new HashSet<>();
        MatchEntity match = new MatchEntity();
        CombatLogEntryEntity entry1 = new CombatLogEntryEntity();
        entry1.setType(CombatLogEntryEntity.Type.ITEM_PURCHASED);
        entry1.setActor("hero1");
        entry1.setItem("item1");
        entry1.setTimestamp(new Date().getTime());
        entries.add(entry1);

        CombatLogEntryEntity entry2 = new CombatLogEntryEntity();
        entry2.setType(CombatLogEntryEntity.Type.ITEM_PURCHASED);
        entry2.setActor("hero2");
        entry2.setItem("item2");
        entry1.setTimestamp(new Date().getTime());
        entries.add(entry1);
        match.setCombatLogEntries(entries);

        Mockito.when(matchService.findById(matchId)).thenReturn(Optional.of(match));

        // Perform the GET request
        MvcResult mvcResult = mvc.perform(get("/api/match/" + matchId + "/" + heroName + "/items")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the response body
        String responseBody = mvcResult.getResponse().getContentAsString();
        List<HeroItem> heroItems = objectMapper.readValue(responseBody, new TypeReference<List<HeroItem>>() {});

        // Verify the response
        assertThat(heroItems.size() == 1);
        assertThat(heroItems.get(0).getItem().equals("item1"));
    }

    @Test
    void testGetHeroSpells() throws Exception {
        // Create test match
        Long matchId = 1L;
        MatchEntity match = new MatchEntity();
        match.setId(matchId);

        // Create test combat log entries
        CombatLogEntryEntity entry1 = new CombatLogEntryEntity();
        entry1.setType(CombatLogEntryEntity.Type.SPELL_CAST);
        entry1.setActor("hero1");
        entry1.setAbility("spell1");
        entry1.setTimestamp(new Date().getTime());

        CombatLogEntryEntity entry2 = new CombatLogEntryEntity();
        entry2.setType(CombatLogEntryEntity.Type.SPELL_CAST);
        entry2.setActor("hero1");
        entry2.setAbility("spell2");
        entry1.setTimestamp(new Date().getTime());

        CombatLogEntryEntity entry3 = new CombatLogEntryEntity();
        entry3.setType(CombatLogEntryEntity.Type.SPELL_CAST);
        entry3.setActor("hero1");
        entry3.setAbility("spell1");
        entry1.setTimestamp(new Date().getTime());

        CombatLogEntryEntity entry4 = new CombatLogEntryEntity();
        entry4.setType(CombatLogEntryEntity.Type.SPELL_CAST);
        entry4.setActor("hero2");
        entry4.setAbility("spell3");
        entry1.setTimestamp(new Date().getTime());


        match.setCombatLogEntries(Set.of(entry1, entry2, entry3, entry4));

        // Save test match to repository
        when(matchService.findById(matchId)).thenReturn(Optional.of(match));

        // Send GET request to endpoint
        MvcResult mvcResult = mvc.perform(get("/api/match/1/hero1/spells")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Verify response body
        String expectedResponseBody = "[{\"spellName\":\"spell1\",\"castCount\":2},{\"spellName\":\"spell2\",\"castCount\":1},{\"spellName\":\"spell3\",\"castCount\":1}]";
        assertThat(expectedResponseBody.equals(mvcResult.getResponse().getContentAsString()));
    }

    @Test
    void testGetHeroDamages() throws Exception {
        // Create a match and some combat log entries
        Long matchId = 1L;
        MatchEntity match = new MatchEntity();
        match.setId(matchId);
        Set<CombatLogEntryEntity> entries = new HashSet<>();

        CombatLogEntryEntity entry1 = new CombatLogEntryEntity();
        entry1.setType(CombatLogEntryEntity.Type.DAMAGE_DONE);
        entry1.setDamage(100);
        entry1.setActor("attacker1");
        entry1.setTarget("hero1");
        entries.add(entry1);

        CombatLogEntryEntity entry2 = new CombatLogEntryEntity();
        entry2.setType(CombatLogEntryEntity.Type.DAMAGE_DONE);
        entry2.setDamage(200);
        entry2.setActor("attacker2");
        entry2.setTarget("hero1");
        entries.add(entry2);

        CombatLogEntryEntity entry3 = new CombatLogEntryEntity();
        entry3.setType(CombatLogEntryEntity.Type.DAMAGE_DONE);
        entry3.setDamage(300);
        entry3.setActor("attacker1");
        entry3.setTarget("hero1");
        entries.add(entry3);
        match.setCombatLogEntries(entries);

        when(matchService.findById(matchId)).thenReturn(Optional.of(match));

        // Perform the request
        MvcResult result = mvc.perform(get("/api/match/1/hero1/damage")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Verify the response
        List<HeroDamage> heroDamages = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<List<HeroDamage>>() {});
        assertThat(heroDamages).hasSize(2);
        assertThat(heroDamages.get(0).getTarget()).isEqualTo("attacker1");
        assertThat(heroDamages.get(0).getDamageInstances()).isEqualTo(2);
        assertThat(heroDamages.get(0).getTotalDamage()).isEqualTo(400);
        assertThat(heroDamages.get(1).getTarget()).isEqualTo("attacker2");
        assertThat(heroDamages.get(1).getDamageInstances()).isEqualTo(1);
        assertThat(heroDamages.get(1).getTotalDamage()).isEqualTo(200);
    }

    /**
     * Helper method that ingests a combat log file and returns the match id associated with all parsed events.
     *
     * @param file file path as a classpath resource, e.g.: /data/combatlog_1.log.txt.
     * @return the id of the match associated with the events parsed from the given file
     * @throws Exception if an error happens when reading or ingesting the file
     */
    private Long ingestMatch(String file) throws Exception {
        String fileContent = IOUtils.resourceToString(file, StandardCharsets.UTF_8);

        return Long.parseLong(mvc.perform(post("/api/match")
                                         .contentType(MediaType.TEXT_PLAIN)
                                         .content(fileContent))
                                 .andReturn()
                                 .getResponse()
                                 .getContentAsString());
    }
}
