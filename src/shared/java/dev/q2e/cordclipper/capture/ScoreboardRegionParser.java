package dev.q2e.cordclipper.capture;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses region labels from the plain text already rendered in the sidebar. */
public final class ScoreboardRegionParser {
    private static final Pattern LABELED_REGION = Pattern.compile(
            "(?i)\\b(?:server\\s+)?(?:region|location|geo|datacenter|data[\\s_-]*center|cluster)\\b"
                    + "\\s*(?:code\\b)?\\s*[:=\\-\\x{2013}\\x{2014}|\\x{2022}]?\\s*(.+?)\\s*$"
    );
    private static final Pattern PING_CONTEXT = Pattern.compile(
            "(?i)\\b(?:ping|latency|rtt)\\b|\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|msec|milliseconds?)\\b"
    );
    private static final Pattern NO_REGION_VALUE = Pattern.compile(
            "(?i)^(?:n\\s*/\\s*a|not\\s+available|unavailable|none|unknown|--+)$"
    );
    private static final Set<String> MISSING_REGION_VALUES = Set.of("n a", "not available", "unavailable", "none", "unknown");
    private static final Pattern PARENTHETICAL = Pattern.compile(
            "[\\(\\[\\{<]\\s*([^\\)\\]\\}>]{1,48}?)\\s*[\\)\\]\\}>]"
    );
    private static final Pattern REGION_BEFORE_PING = Pattern.compile(
            "(?i)^\\s*(.{1,64}?)\\s*[|\\x{2022}\\x{2013}\\x{2014}-]+\\s*(?:ping|latency|rtt)\\b"
    );
    private static final Pattern REGION_AFTER_LATENCY = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:(?:ms|msec|milliseconds?)\\b)?\\s*(.*)$"
    );
    private static final Pattern REGION_BEFORE_LATENCY = Pattern.compile(
            "(?i)\\b(?:ping|latency|rtt)\\b\\s*[:=\\-]?\\s*(.*?)\\s*"
                    + "\\b\\d+(?:[.,]\\d+)?\\s*(?:(?:ms|msec|milliseconds?)\\b)?"
    );
    private static final Pattern LATENCY = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|msec|milliseconds?)\\b"
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("\\b\\d+(?:[.,]\\d+)?\\b");
    private static final Pattern REGION_WORDS = Pattern.compile("[\\p{L}]+(?: [\\p{L}]+){0,5}");
    private static final Pattern NON_REGION_TERMS = Pattern.compile(
            "(?i)\\b(?:tps|cps|fps|mspt|kdr|kills?|deaths?|crystals?|online|players?|shop|survival|skyblock|team|rank|coins?|money|ping|latency|rtt)\\b"
    );
    private static final Pattern IGNORED_LABELS = Pattern.compile(
            "(?i)\\b(?:ping|latency|region|location|server)\\b"
    );
    private static final Pattern CODE = Pattern.compile("[\\p{L}][\\p{L}\\p{N}]{1,11}");
    private static final Pattern REGION_ALIAS_CODE = Pattern.compile("[a-z0-9]{2,12}");
    private static final Set<String> BASE_REGION_CODES = Set.of("na", "eu", "as", "sa", "af", "me", "oc");

    private static final Map<String, String> REGION_NAMES = Map.ofEntries(
            Map.entry("na", "North America (NA)"),
            Map.entry("nam", "North America (NAM)"),
            Map.entry("north america", "North America (NA)"),
            Map.entry("nae", "North America East (NAE)"),
            Map.entry("na east", "North America East (NAE)"),
            Map.entry("na e", "North America East (NAE)"),
            Map.entry("use", "North America East (NAE)"),
            Map.entry("us east", "North America East (NAE)"),
            Map.entry("us e", "North America East (NAE)"),
            Map.entry("us eastern", "North America East (NAE)"),
            Map.entry("usa east", "North America East (NAE)"),
            Map.entry("north america eastern", "North America East (NAE)"),
            Map.entry("north america east", "North America East (NAE)"),
            Map.entry("north american east", "North America East (NAE)"),
            Map.entry("naw", "North America West (NAW)"),
            Map.entry("na west", "North America West (NAW)"),
            Map.entry("na w", "North America West (NAW)"),
            Map.entry("usw", "North America West (NAW)"),
            Map.entry("us west", "North America West (NAW)"),
            Map.entry("us w", "North America West (NAW)"),
            Map.entry("us western", "North America West (NAW)"),
            Map.entry("usa west", "North America West (NAW)"),
            Map.entry("north america western", "North America West (NAW)"),
            Map.entry("north america west", "North America West (NAW)"),
            Map.entry("north american west", "North America West (NAW)"),
            Map.entry("nac", "North America Central (NAC)"),
            Map.entry("na central", "North America Central (NAC)"),
            Map.entry("na c", "North America Central (NAC)"),
            Map.entry("usc", "North America Central (NAC)"),
            Map.entry("us central", "North America Central (NAC)"),
            Map.entry("us c", "North America Central (NAC)"),
            Map.entry("north america central", "North America Central (NAC)"),
            Map.entry("nas", "North America South (NAS)"),
            Map.entry("na south", "North America South (NAS)"),
            Map.entry("north america south", "North America South (NAS)"),
            Map.entry("can", "Canada (CAN)"),
            Map.entry("canada", "Canada (CAN)"),
            Map.entry("ca central", "Canada Central (CA Central)"),
            Map.entry("ca east", "Canada East (CA East)"),
            Map.entry("ca west", "Canada West (CA West)"),
            Map.entry("cam", "Central America (CAM)"),
            Map.entry("central america", "Central America (CAM)"),
            Map.entry("car", "Caribbean (CAR)"),
            Map.entry("caribbean", "Caribbean (CAR)"),
            Map.entry("eu", "Europe (EU)"),
            Map.entry("europe", "Europe (EU)"),
            Map.entry("eue", "Europe East (EUE)"),
            Map.entry("eu east", "Europe East (EUE)"),
            Map.entry("eu e", "Europe East (EUE)"),
            Map.entry("europe eastern", "Europe East (EUE)"),
            Map.entry("euw", "Europe West (EUW)"),
            Map.entry("eu west", "Europe West (EUW)"),
            Map.entry("eu w", "Europe West (EUW)"),
            Map.entry("europe western", "Europe West (EUW)"),
            Map.entry("eun", "Europe North (EUN)"),
            Map.entry("eu north", "Europe North (EUN)"),
            Map.entry("eu n", "Europe North (EUN)"),
            Map.entry("eus", "Europe South (EUS)"),
            Map.entry("eu south", "Europe South (EUS)"),
            Map.entry("eu s", "Europe South (EUS)"),
            Map.entry("euc", "Europe Central (EUC)"),
            Map.entry("eu central", "Europe Central (EUC)"),
            Map.entry("eu c", "Europe Central (EUC)"),
            Map.entry("emea", "Europe, Middle East & Africa (EMEA)"),
            Map.entry("europe middle east africa", "Europe, Middle East & Africa (EMEA)"),
            Map.entry("as", "Asia (AS)"),
            Map.entry("asia", "Asia (AS)"),
            Map.entry("ase", "Asia East (ASE)"),
            Map.entry("asia east", "Asia East (ASE)"),
            Map.entry("east asia", "Asia East (ASE)"),
            Map.entry("asia northeast", "Asia Northeast (Asia NE)"),
            Map.entry("asw", "Asia West (ASW)"),
            Map.entry("asia west", "Asia West (ASW)"),
            Map.entry("asia south", "Asia South (Asia S)"),
            Map.entry("south asia", "Asia South (Asia S)"),
            Map.entry("sea", "Southeast Asia (SEA)"),
            Map.entry("southeast asia", "Southeast Asia (SEA)"),
            Map.entry("south east asia", "Southeast Asia (SEA)"),
            Map.entry("apac", "Asia Pacific (APAC)"),
            Map.entry("asia pacific", "Asia Pacific (APAC)"),
            Map.entry("ap southeast", "Southeast Asia (APAC)"),
            Map.entry("ap south", "South Asia (APAC)"),
            Map.entry("ap northeast", "Northeast Asia (APAC)"),
            Map.entry("oce", "Oceania (OCE)"),
            Map.entry("oc", "Oceania (OCE)"),
            Map.entry("oceania", "Oceania (OCE)"),
            Map.entry("au", "Oceania (OCE)"),
            Map.entry("aus", "Oceania (OCE)"),
            Map.entry("anz", "Australia and New Zealand (ANZ)"),
            Map.entry("new zealand", "New Zealand (NZ)"),
            Map.entry("nz", "New Zealand (NZ)"),
            Map.entry("sa", "South America (SA)"),
            Map.entry("sam", "South America (SA)"),
            Map.entry("south america", "South America (SA)"),
            Map.entry("sae", "South America East (SAE)"),
            Map.entry("sa east", "South America East (SAE)"),
            Map.entry("south america east", "South America East (SAE)"),
            Map.entry("saw", "South America West (SAW)"),
            Map.entry("sa west", "South America West (SAW)"),
            Map.entry("south america west", "South America West (SAW)"),
            Map.entry("latam", "Latin America (LATAM)"),
            Map.entry("latin america", "Latin America (LATAM)"),
            Map.entry("br", "Brazil (BR)"),
            Map.entry("bra", "Brazil (BR)"),
            Map.entry("brazil", "Brazil (BR)"),
            Map.entry("af", "Africa (AF)"),
            Map.entry("afr", "Africa (AF)"),
            Map.entry("africa", "Africa (AF)"),
            Map.entry("afe", "Africa East (AFE)"),
            Map.entry("af east", "Africa East (AFE)"),
            Map.entry("afw", "Africa West (AFW)"),
            Map.entry("af west", "Africa West (AFW)"),
            Map.entry("afs", "Africa South (AFS)"),
            Map.entry("af south", "Africa South (AFS)"),
            Map.entry("south africa", "South Africa (ZA)"),
            Map.entry("za", "South Africa (ZA)"),
            Map.entry("me south", "Middle East South (ME South)"),
            Map.entry("me north", "Middle East North (ME North)"),
            Map.entry("me", "Middle East (ME)"),
            Map.entry("mena", "Middle East (ME)"),
            Map.entry("middle east", "Middle East (ME)"),
            Map.entry("middle eastern", "Middle East (ME)"),
            Map.entry("gulf", "Middle East (Gulf)")
    );
    private static final Pattern KNOWN_REGION_TOKEN = Pattern.compile(
            "(?i)\\b(" + REGION_NAMES.keySet().stream()
                    .filter(key -> REGION_ALIAS_CODE.matcher(key).matches())
                    .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                    .map(Pattern::quote)
                    .collect(java.util.stream.Collectors.joining("|")) + ")\\b"
    );
    private static final Pattern REGION_CODE_TOKEN = Pattern.compile(
            "(?i)\\b(" + REGION_NAMES.values().stream()
                    .map(ScoreboardRegionParser::codeFromDisplayName)
                    .filter(code -> code != null)
                    .filter(code -> !BASE_REGION_CODES.contains(code.toLowerCase(Locale.ROOT)))
                    .distinct()
                    .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                    .map(Pattern::quote)
                    .collect(java.util.stream.Collectors.joining("|")) + ")\\b"
    );
    private static final Set<String> KNOWN_REGION_NAMES = Set.copyOf(REGION_NAMES.values());
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
            if (region.isPresent() && KNOWN_REGION_NAMES.contains(region.get())) {
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

            Matcher beforePing = REGION_BEFORE_PING.matcher(line);
            if (beforePing.find()) {
                Optional<String> region = normalizeRegion(beforePing.group(1));
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
        if (NO_REGION_VALUE.matcher(candidate).matches()) {
            return Optional.empty();
        }
        candidate = LATENCY.matcher(candidate).replaceAll(" ");
        candidate = IGNORED_LABELS.matcher(candidate).replaceAll(" ");
        candidate = NON_ALPHANUMERIC.matcher(candidate).replaceAll(" ").trim();
        candidate = WHITESPACE.matcher(candidate).replaceAll(" ");
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        String key = candidate.toLowerCase(Locale.ROOT);
        if (MISSING_REGION_VALUES.contains(key)) {
            return Optional.empty();
        }
        String display = REGION_NAMES.get(key);
        if (display != null) {
            return Optional.of(display);
        }

        // Prefer a specific code if a scoreboard displays both its broad region and code.
        Matcher regionCode = REGION_CODE_TOKEN.matcher(key);
        if (regionCode.find()) {
            display = REGION_NAMES.get(regionCode.group(1).toLowerCase(Locale.ROOT));
            if (display != null) {
                return Optional.of(display);
            }
        }

        // Keep numbered region identifiers such as NA1 or EU2 intact.
        if (CODE.matcher(key).matches()) {
            return NON_REGION_TERMS.matcher(key).find()
                    ? Optional.empty()
                    : Optional.of(key.toUpperCase(Locale.ROOT));
        }

        key = NUMERIC_TOKEN.matcher(key).replaceAll(" ");
        key = WHITESPACE.matcher(key).replaceAll(" ").trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        display = REGION_NAMES.get(key);
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

        if (NON_REGION_TERMS.matcher(key).find()) {
            return Optional.empty();
        }

        if (CODE.matcher(key).matches()) {
            return Optional.of(key.toUpperCase(Locale.ROOT));
        }

        if (REGION_WORDS.matcher(key).matches()) {
            return Optional.of(toTitleCase(key));
        }

        return Optional.empty();
    }

    private static String codeFromDisplayName(String displayName) {
        int codeStart = displayName.lastIndexOf('(');
        if (codeStart < 0 || !displayName.endsWith(")")) {
            return null;
        }
        String code = displayName.substring(codeStart + 1, displayName.length() - 1);
        return REGION_ALIAS_CODE.matcher(code.toLowerCase(Locale.ROOT)).matches()
                ? code
                : null;
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
