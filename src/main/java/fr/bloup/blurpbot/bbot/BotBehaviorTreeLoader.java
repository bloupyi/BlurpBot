package fr.bloup.blurpbot.bbot;

import org.bukkit.plugin.java.JavaPlugin;

import fr.bloup.blurpbot.bbot.BbotModel.Document;
import fr.bloup.blurpbot.bbot.BbotModel.SetDirective;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.decision.BehaviorTree;
import fr.bloup.blurpbot.decision.priority.PrioritySelectorNode;
import fr.bloup.blurpbot.decision.priority.nodes.IdlePriorityNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;

public final class BotBehaviorTreeLoader {

    /** Même contenu que {@code src/main/resources/trees/default.bbot} — source unique pour le fallback. */
    private static final String DEFAULT_BBOT_RESOURCE = "/trees/default.bbot";

    private BotBehaviorTreeLoader() {
    }

    public static BehaviorTree loadOrBuiltin(JavaPlugin plugin, BotSettings settings) {
        Document doc = readDocument(plugin, settings);
        if (doc != null) {
            return buildDocument(plugin, doc, settings);
        }
        return loadBuiltinTree(plugin, settings);
    }

    private static BehaviorTree buildDocument(JavaPlugin plugin, Document doc, BotSettings settings) {
        for (SetDirective s : doc.sets()) {
            boolean ok = settings.apply(s.key(), s.value());
            if (!ok && plugin != null) {
                plugin.getLogger().warning("[bbot] Ignored invalid set: " + s.key() + "=" + s.value());
            }
        }
        try {
            return BbotTreeBuilder.build(doc);
        } catch (BbotParseException ex) {
            if (plugin != null) {
                plugin.getLogger().log(Level.WARNING, "[bbot] Build error: " + ex.getMessage(), ex);
            }
            return loadBuiltinTree(plugin, settings);
        }
    }

    /**
     * Arbre par défaut : même fichier que sur disque, lu depuis le JAR ({@value #DEFAULT_BBOT_RESOURCE}).
     * En dernier recours : un seul nœud idle minimal.
     */
    public static BehaviorTree builtinDefault(BotSettings settings) {
        BehaviorTree fromJar = tryClasspathDefaultTree(settings);
        if (fromJar != null) {
            return fromJar;
        }
        return emergencyIdleOnly();
    }

    public static BehaviorTree builtinDefault() {
        return builtinDefault(new BotSettings());
    }

    public static String readBotNameOrDefault(JavaPlugin plugin, BotSettings settings, String fallbackName) {
        Document doc = readDocument(plugin, settings);
        if (doc != null && doc.bot() != null) {
            String name = doc.bot().name();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        Document embedded = parseClasspathDocument(DEFAULT_BBOT_RESOURCE);
        if (embedded != null && embedded.bot() != null) {
            String name = embedded.bot().name();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return fallbackName;
    }

    private static Document readDocument(JavaPlugin plugin, BotSettings settings) {
        if (plugin == null) {
            return null;
        }
        String rel = settings.getBehaviorTreeFile();
        if (rel == null || rel.isBlank()) {
            plugin.getLogger().warning("[bbot] behavior-tree path empty, using built-in tree.");
            return null;
        }
        java.io.File f = new java.io.File(plugin.getDataFolder(), rel);
        if (!f.isFile()) {
            plugin.getLogger().warning("[bbot] Behavior tree file not found: " + f.getPath() + " — using built-in tree.");
            return null;
        }
        try {
            String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            return BbotParser.parse(src);
        } catch (BbotParseException ex) {
            plugin.getLogger().log(Level.WARNING, "[bbot] Parse error: " + ex.getMessage(), ex);
            return null;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "[bbot] Could not read " + f.getPath(), ex);
            return null;
        }
    }

    private static BehaviorTree loadBuiltinTree(JavaPlugin plugin, BotSettings settings) {
        BehaviorTree fromJar = tryClasspathDefaultTree(settings);
        if (fromJar != null) {
            return fromJar;
        }
        if (plugin != null) {
            plugin.getLogger().warning("[bbot] Embedded default.bbot failed; using minimal idle tree.");
        }
        return emergencyIdleOnly();
    }

    private static BehaviorTree tryClasspathDefaultTree(BotSettings settings) {
        Document doc = parseClasspathDocument(DEFAULT_BBOT_RESOURCE);
        if (doc == null) {
            return null;
        }
        for (SetDirective s : doc.sets()) {
            settings.apply(s.key(), s.value());
        }
        try {
            return BbotTreeBuilder.build(doc);
        } catch (BbotParseException ex) {
            return null;
        }
    }

    private static Document parseClasspathDocument(String resourcePath) {
        try (InputStream in = BotBehaviorTreeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            String src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return BbotParser.parse(src);
        } catch (BbotParseException | IOException ex) {
            return null;
        }
    }

    private static BehaviorTree emergencyIdleOnly() {
        return new BehaviorTree(
                new PrioritySelectorNode(List.of(new IdlePriorityNode()))
        );
    }
}
