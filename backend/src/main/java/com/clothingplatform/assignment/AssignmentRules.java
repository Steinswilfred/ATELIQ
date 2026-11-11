package com.clothingplatform.assignment;

import com.clothingplatform.settings.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Admin-editable rules (Website settings → Assignment rules). Defaults apply if a setting is missing. */
public record AssignmentRules(boolean requireAvailable, boolean respectCapacity, int escalationHours) {

    @Service
    public static class Loader {
        private final SettingsService settings;
        public Loader(SettingsService settings) { this.settings = settings; }

        public AssignmentRules load() {
            Map<String, JsonNode> s = settings.allSettings();
            return new AssignmentRules(bool(s, "assignment.requireAvailable"), bool(s, "assignment.respectCapacity"),
                    s.containsKey("assignment.escalationHours") && s.get("assignment.escalationHours").isNumber() ? s.get("assignment.escalationHours").asInt(24) : 24);
        }
        private static boolean bool(Map<String, JsonNode> s, String key) { return !s.containsKey(key) || s.get(key).asBoolean(true); }
    }
}
