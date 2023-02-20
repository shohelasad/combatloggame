package gg.bayes.challenge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.bayes.challenge.persistence.model.CombatLogEntryEntity;
import gg.bayes.challenge.persistence.model.MatchEntity;
import gg.bayes.challenge.persistence.repository.CombatLogEntryRepository;
import gg.bayes.challenge.persistence.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CombatLogParserService {
    private final MatchRepository matchRepository;
    private final CombatLogEntryRepository combatLogEntryRepository;

    @Autowired
    public CombatLogParserService(MatchRepository matchRepository, CombatLogEntryRepository combatLogEntryRepository) {
        this.matchRepository = matchRepository;
        this.combatLogEntryRepository = combatLogEntryRepository;
    }

    private final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\[(.*?)\\]");
    private final Pattern purchaseItemPattern = Pattern.compile("\\[.*?\\] npc_dota_hero_(?<actor>[^\\s]+) buys item item_(?<item>[^\\s]+)");
    private final Pattern heroKilledPattern = Pattern.compile("\\[.*?\\] npc_dota_hero_(?<actor>[^\\s]+) kills npc_dota_hero_(?<target>[^\\s]+) with (?<ability>[^\\s]+) Level (?<abilityLevel>\\d+)");
    private final Pattern spellCastPattern = Pattern.compile("\\[.*?\\] npc_dota_hero_(?<actor>[^\\s]+) casts ability (?<ability>[^\\s]+) \\(lvl (?<abilityLevel>\\d+)\\) on npc_dota_hero_(?<target>[^\\s]+)");
    private final Pattern damageDonePattern = Pattern.compile("\\[.*?\\] npc_dota_hero_(?<actor>[^\\s]+) hits npc_dota_hero_(?<target>[^\\s]+) with (?<damage>\\d+) damage");

    public Long parseAndSave(String combatLog) {
        Set<CombatLogEntryEntity> entries = new HashSet<>();
        MatchEntity match = new MatchEntity();
        String[] lines = combatLog.split("\\r?\\n");
        for (String line : lines) {
            Matcher purchaseItemMatcher = purchaseItemPattern.matcher(line);
            Matcher heroKilledMatcher = heroKilledPattern.matcher(line);
            Matcher spellCastMatcher = spellCastPattern.matcher(line);
            Matcher damageDoneMatcher = damageDonePattern.matcher(line);
            CombatLogEntryEntity entry = new CombatLogEntryEntity();
            if (purchaseItemMatcher.find()) {
                entry.setMatch(match);
                entry.setTimestamp(extractTimestamp(line));
                entry.setType(CombatLogEntryEntity.Type.ITEM_PURCHASED);
                entry.setActor(purchaseItemMatcher.group("actor"));
                entry.setItem(purchaseItemMatcher.group("item"));
                entries.add(entry);
            } else if (heroKilledMatcher.find()) {
                entry.setMatch(match);
                entry.setTimestamp(extractTimestamp(line));
                entry.setType(CombatLogEntryEntity.Type.HERO_KILLED);
                entry.setActor(heroKilledMatcher.group("actor"));
                entry.setTarget(heroKilledMatcher.group("target"));
                entry.setAbility(heroKilledMatcher.group("ability"));
                entry.setAbilityLevel(Integer.parseInt(heroKilledMatcher.group("abilityLevel")));
                entries.add(entry);
            } else if (spellCastMatcher.find()) {
                entry.setMatch(match);
                entry.setTimestamp(extractTimestamp(line));
                entry.setType(CombatLogEntryEntity.Type.SPELL_CAST);
                entry.setActor(spellCastMatcher.group("actor"));
                entry.setTarget(spellCastMatcher.group("target"));
                entry.setAbility(spellCastMatcher.group("ability"));
                entries.add(entry);
            } else if (damageDoneMatcher.find()) {
                entry.setMatch(match);
                entry.setTimestamp(extractTimestamp(line));
                entry.setType(CombatLogEntryEntity.Type.DAMAGE_DONE);
                entry.setActor(spellCastMatcher.group("actor"));
                entry.setTarget(spellCastMatcher.group("target"));
                entry.setAbility(spellCastMatcher.group("ability"));
                entry.setAbilityLevel(Integer.parseInt(heroKilledMatcher.group("abilityLevel")));
                entries.add(entry);

            }
        }
        match.setCombatLogEntries(entries);
        return matchRepository.save(match).getId();
    }

    private long extractTimestamp(String line) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        if (matcher.find()) {
            String timestampString = matcher.group(1);
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
            try {
                Date timestamp = dateFormat.parse(timestampString);
                return timestamp.getTime();
            } catch (ParseException e) {
                //throw new RuntimeException("Error parsing timestamp: " + timestampString, e); //commented out to process i=other fields
            }
        }

        return 0;
    }


}