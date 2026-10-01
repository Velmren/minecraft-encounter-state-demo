package dev.velmren.encounter.paper;

import dev.velmren.encounter.core.EncounterDefinition;
import dev.velmren.encounter.core.EncounterEngine;
import dev.velmren.encounter.core.ObjectiveDefinition;
import dev.velmren.encounter.core.PhaseDefinition;
import dev.velmren.encounter.core.TransitionResult;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin Paper adapter that loads the encounter definition from config.yml. */
public final class PaperEncounterPlugin extends JavaPlugin {
    private EncounterEngine encounter;
    private ObservatoryArena arena;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            EncounterDefinition definition = loadDefinition();
            if (!definition.phases().equals(List.of(
                    new PhaseDefinition("stabilize", List.of(new ObjectiveDefinition("activate_relays", 2), new ObjectiveDefinition("clear_sentinels", 3))),
                    new PhaseDefinition("seal_rift", List.of(new ObjectiveDefinition("hold_zone_ticks", 15)))))) {
                throw new IllegalArgumentException("The observatory map requires stabilize: relays=2, sentinels=3; seal_rift: hold_zone_ticks=15");
            }
            encounter = new EncounterEngine(definition);
            arena = new ObservatoryArena(this, encounter);
            arena.prepare();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            getLogger().severe("Invalid encounter configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() { if (arena != null) arena.close(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(renderStatus());
            return true;
        }

        TransitionResult result;
        switch (args[0].toLowerCase()) {
            case "join", "kit" -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    if (args[0].equalsIgnoreCase("join")) arena.join(player); else arena.kit(player);
                } else sender.sendMessage("This command requires a player.");
                return true;
            }
            case "start" -> result = arena.start();
            case "reset" -> {
                if (!sender.hasPermission("encounter.admin")) { sender.sendMessage("Only an operator can reset an active encounter."); return true; }
                result = arena.reset();
            }
            case "status" -> {
                sender.sendMessage(renderStatus());
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /encounter <join|kit|start|status|reset>");
                return true;
            }
        }

        sender.sendMessage(result.accepted()
                ? "Accepted: " + result.events()
                : "Rejected: " + result.reason());
        return true;
    }

    private String renderStatus() {
        var snapshot = encounter.snapshot();
        return "Encounter " + snapshot.encounterId() + ": " + snapshot.status()
                + ", phase=" + snapshot.activePhaseId()
                + ", progress=" + snapshot.objectiveProgress();
    }

    private EncounterDefinition loadDefinition() {
        ConfigurationSection root = requireSection(getConfig(), "encounter");
        String encounterId = requireText(root, "id");
        List<?> phaseRows = root.getList("phases");
        if (phaseRows == null || phaseRows.isEmpty()) {
            throw new IllegalArgumentException("encounter.phases must contain at least one phase");
        }

        List<PhaseDefinition> phases = phaseRows.stream().map(row -> {
            if (!(row instanceof java.util.Map<?, ?> phaseMap)) {
                throw new IllegalArgumentException("each phase must be a map");
            }
            Object phaseId = phaseMap.get("id");
            Object objectiveRows = phaseMap.get("objectives");
            if (!(phaseId instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("phase id must be non-blank text");
            }
            if (!(objectiveRows instanceof List<?> rows) || rows.isEmpty()) {
                throw new IllegalArgumentException("phase " + id + " must contain objectives");
            }
            List<ObjectiveDefinition> objectives = rows.stream().map(objectiveRow -> {
                if (!(objectiveRow instanceof java.util.Map<?, ?> objectiveMap)) {
                    throw new IllegalArgumentException("each objective must be a map");
                }
                Object objectiveId = objectiveMap.get("id");
                Object required = objectiveMap.get("required-progress");
                if (!(objectiveId instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("objective id must be non-blank text");
                }
                if (!(required instanceof Number number)) {
                    throw new IllegalArgumentException("required-progress must be a number for " + text);
                }
                int requiredProgress;
                try {
                    requiredProgress = new BigDecimal(number.toString()).intValueExact();
                } catch (ArithmeticException | NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "required-progress must be a whole 32-bit integer for " + text, exception);
                }
                return new ObjectiveDefinition(text, requiredProgress);
            }).toList();
            return new PhaseDefinition(id, objectives);
        }).toList();
        return new EncounterDefinition(encounterId, phases);
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException(key + " section is required");
        }
        return section;
    }

    private static String requireText(ConfigurationSection section, String key) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " is required");
        }
        return value;
    }
}
