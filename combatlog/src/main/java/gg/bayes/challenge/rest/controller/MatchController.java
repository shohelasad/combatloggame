package gg.bayes.challenge.rest.controller;

import gg.bayes.challenge.persistence.model.CombatLogEntryEntity;
import gg.bayes.challenge.persistence.model.MatchEntity;
import gg.bayes.challenge.persistence.repository.CombatLogEntryRepository;
import gg.bayes.challenge.persistence.repository.MatchRepository;
import gg.bayes.challenge.rest.model.HeroDamage;
import gg.bayes.challenge.rest.model.HeroItem;
import gg.bayes.challenge.rest.model.HeroKills;
import gg.bayes.challenge.rest.model.HeroSpells;
import gg.bayes.challenge.service.CombatLogParserService;
import gg.bayes.challenge.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.constraints.NotBlank;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/match")
@Validated
public class MatchController {

    private CombatLogParserService combatLogParserService;
    private MatchService matchService;
    private CombatLogEntryRepository combatLogEntryRepository;

    @Autowired
    public MatchController(CombatLogParserService combatLogParserService, MatchService matchService, CombatLogEntryRepository combatLogEntryRepository) {
       this.combatLogParserService = combatLogParserService;
       this.matchService = matchService;
       this.combatLogEntryRepository = combatLogEntryRepository;
    }

    /**
     * Ingests a DOTA combat log file, parses and persists relevant events data. All events are associated with the same
     * match id.
     *
     * @param combatLog the content of the combat log file
     * @return the match id associated with the parsed events
     */
    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Long> ingestCombatLog(@RequestBody @NotBlank String combatLog) {
        //throw new NotImplementedException("TODO: implement");
        Long matchId = combatLogParserService.parseAndSave(combatLog);
        return ResponseEntity.ok().body(matchId);
    }

    /**
     * Fetches the heroes and their kill counts for the given match.
     *
     * @param matchId the match identifier
     * @return a collection of heroes and their kill counts
     */
    @GetMapping(
            path = "{matchId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<HeroKills>> getMatch(@PathVariable("matchId") Long matchId) {
        Optional<MatchEntity> matchOptional = matchService.findById(matchId);
        if (matchOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        MatchEntity match = matchOptional.get();
        Set<CombatLogEntryEntity> entries = match.getCombatLogEntries();
        List<HeroKills> heroKills = new ArrayList<>();
        Map<String, HeroKills> heroKillsMap = new HashMap<>();
        Map<String, HeroKills> victimKills = new HashMap<>();

        for (CombatLogEntryEntity entry : entries) {
            if (entry.getType() == CombatLogEntryEntity.Type.HERO_KILLED) {
                String killer = entry.getActor();
                HeroKills killerKills = heroKillsMap.computeIfAbsent(killer, k -> new HeroKills(killer, 0));
                heroKillsMap.put(killer, new HeroKills(killer, killerKills.getKills() + 1));
            }
        }

        heroKills.addAll(heroKillsMap.values());
        return ResponseEntity.ok(heroKills);
    }

    /**
     * For the given match, fetches the items bought by the named hero.
     *
     * @param matchId  the match identifier
     * @param heroName the hero name
     * @return a collection of items bought by the hero during the match
     */
    @GetMapping(
            path = "{matchId}/{heroName}/items",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<HeroItem>> getHeroItems(
            @PathVariable("matchId") Long matchId,
            @PathVariable("heroName") String heroName) {

        List<HeroItem> heroItems = new ArrayList<>();
        MatchEntity match = matchService.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found with id " + matchId));

        for (CombatLogEntryEntity entry : match.getCombatLogEntries()) {
            if (entry.getType() == CombatLogEntryEntity.Type.ITEM_PURCHASED && heroName.equals(entry.getActor())) {
                HeroItem heroItem = new HeroItem(entry.getItem(), entry.getTimestamp());
                heroItems.add(heroItem);
            }
        }

        return ResponseEntity.ok(heroItems);
    }

    /**
     * For the given match, fetches the spells cast by the named hero.
     *
     * @param matchId  the match identifier
     * @param heroName the hero name
     * @return a collection of spells cast by the hero and how many times they were cast
     */
    @GetMapping(
            path = "{matchId}/{heroName}/spells",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<HeroSpells>> getHeroSpells(
            @PathVariable("matchId") Long matchId,
            @PathVariable("heroName") String heroName) {

        MatchEntity match = matchService.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found with id " + matchId));
        List<CombatLogEntryEntity> heroEntries = combatLogEntryRepository.findByMatchAndActor(match, heroName);

        Map<String, List<CombatLogEntryEntity>> spellEntriesMap = heroEntries.stream()
                .filter(entry -> entry.getType() == CombatLogEntryEntity.Type.SPELL_CAST)
                .collect(Collectors.groupingBy(CombatLogEntryEntity::getAbility));

        List<HeroSpells> heroSpells = new ArrayList<>();
        for (Map.Entry<String, List<CombatLogEntryEntity>> entry : spellEntriesMap.entrySet()) {
            String spellName = entry.getKey();
            List<CombatLogEntryEntity> spellEntries = entry.getValue();
            heroSpells.add(new HeroSpells(spellName, spellEntries.size()));
        }

        return ResponseEntity.ok(heroSpells);
    }

    /**
     * For a given match, fetches damage done data for the named hero.
     *
     * @param matchId  the match identifier
     * @param heroName the hero name
     * @return a collection of "damage done" (target, number of times and total damage) elements
     */
    @GetMapping(
            path = "{matchId}/{heroName}/damage",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<HeroDamage>> getHeroDamages(
            @PathVariable("matchId") Long matchId,
            @PathVariable("heroName") String heroName) {
        MatchEntity match = matchService.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found with id " + matchId));

        List<CombatLogEntryEntity> entries = match.getCombatLogEntries()
                .stream()
                .filter(entry ->
                        entry.getType() == CombatLogEntryEntity.Type.DAMAGE_DONE &&
                                entry.getTarget() != null &&
                                entry.getTarget().equals(heroName)
                )
                .collect(Collectors.toList());

        Map<String, Integer> damageByActor = entries
                .stream()
                .filter(entry -> entry.getActor() != null)
                .collect(Collectors.toMap(
                        CombatLogEntryEntity::getActor,
                        CombatLogEntryEntity::getDamage,
                        Integer::sum
                ));

        List<HeroDamage> heroDamages = damageByActor.entrySet()
                .stream()
                .map(entry -> new HeroDamage(
                        entry.getKey(),
                        (int) entries.stream().filter(e -> e.getActor().equals(entry.getKey())).count(),
                        entry.getValue()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(heroDamages);
    }
}
