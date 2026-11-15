package dev.q2e.cordclipper.capture;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses region labels from the plain text already rendered in the sidebar. */
public final class ScoreboardRegionParser {
    private static final Pattern LABELED_REGION = Pattern.compile(
            "(?i)\\b(?:server\\s+)?(?:region|location)\\b\\s*[:=\\-–—|•]?\\s*(.+?)\\s*$"
    );
    private static final Pattern PING_CONTEXT = Pattern.compile(
            "(?i)\\b(?:ping|latency)\\b|\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|msec|milliseconds?)\\b"
    );
    private static final Pattern PARENTHETICAL = Pattern.compile("[\\(\\[]\\s*([^\\)\\]]{1,48}?)\\s*[\\)\\]]");
    private static final Pattern REGION_AFTER_LATENCY = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|msec|milliseconds?)\\b\\s*(.*)$"
    );
    private static final Pattern REGION_BEFORE_LATENCY = Pattern.compile(
            "(?i)\\b(?:ping|latency)\\b\\s*[:=\\-]?\\s*(.*?)\\s*\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|msec|milliseconds?)\\b"
    );
    private static final Pattern KNOWN_REGION_TOKEN = Pattern.compile(
            "(?i)\\b(NAE|NAW|NAC|EUE|EUW|EUN|EUS|EUC|ASE|ASW|OCE|ASIA|APAC|MENA|SAM|AFR|NA|EU|AS|SA|ME|OC)\\b"
    );
    private static final Pattern LATENCY = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|msec|milliseconds?)\\b"
    );
    private static final Pattern IGNORED_LABELS = Pattern.compile(
            "(?i)\\b(?:ping|latency|region|location|server)\\b"
    );
    private static final Pattern CODE = Pattern.compile("(?i)[a-z][a-z0-9]{1,11}");

    private static final Map<String, String> REGION_NAMES = Map.ofEntries(
            Map.entry("na", "North America (NA)"),
            Map.entry("north america", "North America (NA)"),
            Map.entry("nae", "North America East (NAE)"),
            Map.entry("na east", "North America East (NAE)"),
            Map.entry("north america east", "North America East (NAE)"),
            Map.entry("north american east", "North America East (NAE)"),
            Map.entry("naw", "North America West (NAW)"),
            Map.entry("na west", "North America West (NAW)"),
            Map.entry("north america west", "North America West (NAW)"),
            Map.entry("north american west", "North America West (NAW)"),
            Map.entry("nac", "North America Central (NAC)"),
            Map.entry("na central", "North America Central (NAC)"),
            Map.entry("eu", "Europe (EU)"),
            Map.entry("europe", "Europe (EU)"),
            Map.entry("eue", "Europe East (EUE)"),
            Map.entry("eu east", "Europe East (EUE)"),
            Map.entry("euw", "Europe West (EUW)"),
            Map.entry("eu west", "Europe West (EUW)"),
            Map.entry("eun", "Europe North (EUN)"),
            Map.entry("eu north", "Europe North (EUN)"),
            Map.entry("eus", "Europe South (EUS)"),
            Map.entry("eu south", "Europe South (EUS)"),
            Map.entry("euc", "Europe Central (EUC)"),
            Map.entry("eu central", "Europe Central (EUC)"),
            Map.entry("as", "Asia (AS)"),
            Map.entry("asia", "Asia (AS)"),
            Map.entry("ase", "Asia East (ASE)"),
            Map.entry("asia east", "Asia East (ASE)"),
            Map.entry("asw", "Asia West (ASW)"),
            Map.entry("asia west", "Asia West (ASW)"),
            Map.entry("apac", "Asia Pacific (APAC)"),
            Map.entry("oce", "Oceania (OCE)"),
            Map.entry("oc", "Oceania (OCE)"),
            Map.entry("oceania", "Oceania (OCE)"),
            Map.entry("au", "Oceania (OCE)"),
            Map.entry("aus", "Oceania (OCE)"),
            Map.entry("sa", "South America (SA)"),
            Map.entry("sam", "South America (SA)"),
            Map.entry("south america", "South America (SA)"),
            Map.entry("af", "Africa (AF)"),
            Map.entry("afr", "Africa (AF)"),
            Map.entry("africa", "Africa (AF)"),
            Map.entry("me", "Middle East (ME)"),
            Map.entry("mena", "Middle East (ME)"),
            Map.entry("middle east", "Middle East (ME)")
    );
    private static final List<Map.Entry<String, String>> REGION_ALIASES_BY_LENGTH = REGION_NAMES.entrySet().stream()
            .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
            .toList();

    private ScoreboardRegionParser() {
    }

    public static Optional<String> findRegion(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Optional.empty();
        }

        // Explicit region fields take precedence over a ping-line suffix.
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher labeled = LABELED_REGION.matcher(line.replace('\u00A0', ' '));
            if (labeled.find()) {
                Optional<String> region = normalizeRegion(labeled.group(1));
                if (region.isPresent()) {
                    return region;
                }
            }
        }

        // A few scoreboards render the region as its own sidebar row.
        for (String line : lines) {
            Optional<String> region = normalizeRegion(line);
            if (region.isPresent() && REGION_NAMES.containsValue(region.get())) {
                return region;
            }
        }

        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.replace('\u00A0', ' ');
            if (!PING_CONTEXT.matcher(line).find()) {
                continue;
            }

            Matcher parenthetical = PARENTHETICAL.matcher(line);
            while (parenthetical.find()) {
                Optional<String> region = normalizeRegion(parenthetical.group(1));
                if (region.isPresent()) {
                    return region;
                }
            }

            Matcher afterLatency = REGION_AFTER_LATENCY.matcher(line);
            if (afterLatency.find()) {
                Optional<String> region = normalizeRegion(afterLatency.group(1));
                if (region.isPresent()) {
                    return region;
                }
            }

            Matcher beforeLatency = REGION_BEFORE_LATENCY.matcher(line);
            if (beforeLatency.find()) {
                Optional<String> region = normalizeRegion(beforeLatency.group(1));
                if (region.isPresent()) {
                    return region;
                }
            }

            Matcher knownCode = KNOWN_REGION_TOKEN.matcher(line);
            if (knownCode.find()) {
                Optional<String> region = normalizeRegion(knownCode.group(1));
                if (region.isPresent()) {
                    return region;
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<String> normalizeRegion(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        String candidate = rawValue.replace('\u00A0', ' ').trim();
        candidate = LATENCY.matcher(candidate).replaceAll(" ");
        candidate = IGNORED_LABELS.matcher(candidate).replaceAll(" ");
        candidate = candidate.replaceAll("\\b\\d+(?:[.,]\\d+)?\\b", " ");
        candidate = candidate.replaceAll("[^A-Za-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        String key = candidate.toLowerCase(Locale.ROOT);
        String display = REGION_NAMES.get(key);
        if (display != null) {
            return Optional.of(display);
        }

        // Some scoreboards show both the code and its full name in one label.
        for (Map.Entry<String, String> region : REGION_ALIASES_BY_LENGTH) {
            String alias = region.getKey();
            if (alias.length() < 4 && !alias.contains(" ")) {
                continue;
            }
            if (key.startsWith(alias + " ") || key.endsWith(" " + alias) || key.contains(" " + alias + " ")) {
                return Optional.of(region.getValue());
            }
        }

        if (CODE.matcher(key).matches()) {
            return Optional.of(key.toUpperCase(Locale.ROOT));
        }

        if (key.matches("[a-z]+(?: [a-z]+){0,3}")) {
            return Optional.of(toTitleCase(key));
        }

        return Optional.empty();
    }

    private static String toTitleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (String word : value.split(" ")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }
}
