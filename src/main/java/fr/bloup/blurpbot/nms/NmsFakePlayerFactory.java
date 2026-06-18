package fr.bloup.blurpbot.nms;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import fr.bloup.blurpbot.core.BotSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Function;

public final class NmsFakePlayerFactory {
    private NmsFakePlayerFactory() {}

    public static Player spawnFakePlayer(Location spawnLocation, String name, BotSettings settings) {
        World world = spawnLocation.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Spawn world cannot be null");
        }

        try {
            Object craftServer = org.bukkit.Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object serverLevel = world.getClass().getMethod("getHandle").invoke(world);

            UUID uuid = UUID.randomUUID();
            String finalName = sanitizeName(name, uuid);
            String profileName = sanitizeProfileName(settings != null ? settings.getSkinProfileName() : null, finalName, uuid);

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object gameProfile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(uuid, profileName);
            applySkinProperties(gameProfile, settings);

            Class<?> clientInfoClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Object clientInfo = clientInfoClass.getMethod("createDefault").invoke(null);

            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");

            Constructor<?> playerCtor = serverPlayerClass.getConstructor(
                    minecraftServerClass,
                    serverLevelClass,
                    gameProfileClass,
                    clientInfoClass
            );
            Object serverPlayer = playerCtor.newInstance(minecraftServer, serverLevel, gameProfile, clientInfo);

            serverPlayerClass.getMethod("setPos", double.class, double.class, double.class)
                    .invoke(serverPlayer, spawnLocation.getX(), spawnLocation.getY(), spawnLocation.getZ());
            serverPlayerClass.getMethod("setYRot", float.class).invoke(serverPlayer, spawnLocation.getYaw());
            serverPlayerClass.getMethod("setXRot", float.class).invoke(serverPlayer, spawnLocation.getPitch());

            // Build a serverbound in-memory connection with a live channel.
            Class<?> packetFlowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
            @SuppressWarnings("unchecked")
            Object packetFlowServerbound = Enum.valueOf((Class<? extends Enum>) packetFlowClass, "SERVERBOUND");

            Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
            Object connection = connectionClass.getConstructor(packetFlowClass).newInstance(packetFlowServerbound);

            Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
            Object embeddedChannel = embeddedChannelClass.getConstructor().newInstance();
            connectionClass.getField("channel").set(connection, embeddedChannel);
            connectionClass.getField("address").set(connection, new java.net.InetSocketAddress("127.0.0.1", 0));

            Object pipeline = embeddedChannelClass.getMethod("pipeline").invoke(embeddedChannel);
            Class<?> channelPipelineClass = Class.forName("io.netty.channel.ChannelPipeline");
            tryInvokeStatic(connectionClass, "configureInMemoryPipeline", pipeline, packetFlowServerbound);

            // Configure outbound game protocol so server->client packets are serializable.
            Object registryAccess = minecraftServerClass.getMethod("registryAccess").invoke(minecraftServer);
            Class<?> registryFriendlyByteBufClass = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf");
            @SuppressWarnings("unchecked")
            Function<Object, Object> decorator = (Function<Object, Object>)
                    registryFriendlyByteBufClass
                            .getMethod("decorator", Class.forName("net.minecraft.core.RegistryAccess"))
                            .invoke(null, registryAccess);

            setupOutboundProtocol(connectionClass, connection, decorator);

            Class<?> commonCookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            Object cookie = createCommonListenerCookie(commonCookieClass, gameProfile, clientInfo);

            Object playerList = minecraftServerClass.getMethod("getPlayerList").invoke(minecraftServer);
            placeNewPlayer(playerList, connection, serverPlayer, cookie);

            // Le fake player nait dans le gamemode par defaut du serveur (server.properties). En
            // CREATIVE/SPECTATOR, GameType.updatePlayerAbilities met Abilities.invulnerable=true : ce champ
            // (pilote par le gametype, lu par Player.isInvulnerableTo) est DISTINCT du flag Entity.invulnerable
            // que Bukkit setInvulnerable() ecrit. Resultat : un bot ne en spectator reste intapable meme
            // affiche en ADVENTURE. On force SURVIVAL ici pour garantir invulnerable=false quel que soit le
            // gamemode par defaut du serveur.
            forceSurvivalGameType(serverPlayer);

            // Ne pas utiliser Player.teleport() : plugins / logique Paper peuvent envoyer au spawn du monde par défaut.
            // Forcer position + dimension uniquement côté NMS sur le ServerPlayer.
            nmsTeleportServerPlayerAfterPlace(serverPlayer, serverLevel, spawnLocation);

            Player bukkit = (Player) serverPlayerClass.getMethod("getBukkitEntity").invoke(serverPlayer);

            bukkit.setCustomName(finalName);
            bukkit.setCustomNameVisible(true);
            return bukkit;
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to spawn NMS fake player: " + formatDeepestCause(e), e);
        }
    }

    /**
     * Après {@code placeNewPlayer}, le joueur peut se retrouver au spawn du monde par défaut (overworld).
     * On repositionne via {@code Entity#teleportTo(ServerLevel, x,y,z, Set, yaw, pitch, boolean)} (1.21+),
     * sans Player.teleport (événements / spawn forcé).
     */
    private static void nmsTeleportServerPlayerAfterPlace(Object serverPlayer, Object serverLevel, Location loc)
            throws ReflectiveOperationException {
        Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
        Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        try {
            Method m = entityClass.getMethod(
                    "teleportTo",
                    serverLevelClass,
                    double.class,
                    double.class,
                    double.class,
                    java.util.Set.class,
                    float.class,
                    float.class,
                    boolean.class
            );
            m.invoke(serverPlayer, serverLevel, x, y, z, Collections.emptySet(), yaw, pitch, false);
            return;
        } catch (NoSuchMethodException ignored) {
            // autre mapping / fork
        }

        Class<?> slRuntime = serverLevel.getClass();
        for (Method m : entityClass.getMethods()) {
            if (!"teleportTo".equals(m.getName())) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (!p[0].isAssignableFrom(slRuntime)) {
                continue;
            }
            Object[] args = buildTeleportToArgs(p, serverLevel, x, y, z, yaw, pitch);
            if (args == null) {
                continue;
            }
            try {
                m.setAccessible(true);
                m.invoke(serverPlayer, args);
                return;
            } catch (Throwable ignored) {
                // try next overload
            }
        }

        entityClass.getMethod("setPos", double.class, double.class, double.class).invoke(serverPlayer, x, y, z);
        serverPlayer.getClass().getMethod("setYRot", float.class).invoke(serverPlayer, yaw);
        serverPlayer.getClass().getMethod("setXRot", float.class).invoke(serverPlayer, pitch);
    }

    private static Object[] buildTeleportToArgs(Class<?>[] p, Object serverLevel, double x, double y, double z,
            float yaw, float pitch) {
        if (p.length == 6
                && p[1] == double.class && p[2] == double.class && p[3] == double.class
                && p[4] == float.class && p[5] == float.class) {
            return new Object[]{serverLevel, x, y, z, yaw, pitch};
        }
        if (p.length == 8
                && p[1] == double.class && p[2] == double.class && p[3] == double.class
                && java.util.Set.class.isAssignableFrom(p[4])
                && p[5] == float.class && p[6] == float.class && p[7] == boolean.class) {
            return new Object[]{serverLevel, x, y, z, Collections.emptySet(), yaw, pitch, false};
        }
        return null;
    }

    private static String formatDeepestCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
    }

    /**
     * Detects in-memory fake players created by this factory (EmbeddedChannel-backed connection).
     */
    public static boolean isInMemoryFakePlayer(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object serverGamePacketListener = handle.getClass().getField("connection").get(handle);
            Object connection = serverGamePacketListener.getClass().getField("connection").get(serverGamePacketListener);
            Object channel = connection.getClass().getField("channel").get(connection);
            Class<?> embeddedChannelClass = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
            return embeddedChannelClass.isInstance(channel);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sanitizeName(String requestedName, UUID fallbackUuid) {
        String base = (requestedName == null || requestedName.isBlank())
                ? "SB_" + fallbackUuid.toString().substring(0, 6)
                : requestedName;
        if (base.length() > 16) {
            return base.substring(0, 16);
        }
        return base;
    }

    private static String sanitizeProfileName(String requestedProfileName, String fallbackName, UUID fallbackUuid) {
        String base = (requestedProfileName == null || requestedProfileName.isBlank())
                ? fallbackName
                : requestedProfileName;
        return sanitizeName(base, fallbackUuid);
    }

    private static void applySkinProperties(Object gameProfile, BotSettings settings) {
        if (settings == null) {
            return;
        }
        String texture = trimToNull(settings.getSkinTexture());
        if (texture == null) {
            return;
        }
        String signature = trimToNull(settings.getSkinSignature());
        try {
            Object propertyMap = gameProfile.getClass().getMethod("getProperties").invoke(gameProfile);
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Object property = (signature == null)
                    ? propertyClass.getConstructor(String.class, String.class).newInstance("textures", texture)
                    : propertyClass.getConstructor(String.class, String.class, String.class).newInstance("textures", texture, signature);
            propertyMap.getClass().getMethod("put", Object.class, Object.class).invoke(propertyMap, "textures", property);
        } catch (ReflectiveOperationException ignored) {
            // Best effort: bot still spawns even if custom skin properties cannot be applied.
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void applyTabVisibility(Player player, boolean visible) {
        if (player == null) {
            return;
        }
        try {
            // Paper API path when available.
            player.getClass().getMethod("setListed", boolean.class).invoke(player, visible);
        } catch (Throwable ignored) {
            // Best effort only. Some server variants may not expose listed API.
        }
    }

    /**
     * For in-memory fake players, Bukkit rotation updates are sometimes not enough to visually rotate the head.
     * Keep body/head yaw in sync through NMS handle fields/methods when available.
     */
    public static void syncHeadBodyRotation(Player player, float yaw, float pitch) {
        if (player == null) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            tryInvokeInstance(handle, "setYRot", yaw);
            tryInvokeInstance(handle, "setXRot", pitch);
            tryInvokeInstance(handle, "setYHeadRot", yaw);
            tryInvokeInstance(handle, "setYBodyRot", yaw);

            // Fallback for forks/mappings exposing fields instead of methods.
            setFloatFieldIfPresent(handle, "yHeadRot", yaw);
            setFloatFieldIfPresent(handle, "yBodyRot", yaw);
            setFloatFieldIfPresent(handle, "yRotO", yaw);
            setFloatFieldIfPresent(handle, "xRotO", pitch);
        } catch (Throwable ignored) {
            // Best effort only.
        }
    }

    /**
     * 1.21.4+: {@code GameProtocols.CLIENTBOUND_TEMPLATE} is a {@code ProtocolInfo.Unbound} — call {@code bind(decorator)},
     * not {@code SimpleUnboundProtocol} (older / different path). If this is skipped, {@code placeNewPlayer} fails with
     * "Pipeline has no outbound protocol configured".
     */
    private static Object invokeBindUnbound(Object unboundTemplate, Function<Object, Object> decorator)
            throws ReflectiveOperationException {
        Method bind = unboundTemplate.getClass().getMethod("bind", java.util.function.Function.class);
        bind.setAccessible(true);
        return bind.invoke(unboundTemplate, decorator);
    }

    private static void setupOutboundProtocol(Class<?> connectionClass, Object connection, Function<Object, Object> decorator)
            throws ReflectiveOperationException {
        Class<?> gameProtocolsClass = Class.forName("net.minecraft.network.protocol.game.GameProtocols");
        Object clientboundTemplate = gameProtocolsClass.getField("CLIENTBOUND_TEMPLATE").get(null);
        Object outboundProtocolInfo = invokeBindUnbound(clientboundTemplate, decorator);
        Class<?> protocolInfoClass = Class.forName("net.minecraft.network.ProtocolInfo");
        Method setup = resolveSetupOutboundProtocol(connectionClass, protocolInfoClass);
        setup.invoke(connection, outboundProtocolInfo);
    }

    /**
     * Resolves {@code Connection#setupOutboundProtocol(ProtocolInfo)} explicitly. Scanning {@code getMethods()} can pick a
     * bridge or wrong overload on some server builds.
     */
    private static Method resolveSetupOutboundProtocol(Class<?> connectionClass, Class<?> protocolInfoClass)
            throws NoSuchMethodException {
        try {
            Method m = connectionClass.getMethod("setupOutboundProtocol", protocolInfoClass);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
            // Fall back: declared methods (private/package on some forks).
        }
        for (Method m : connectionClass.getDeclaredMethods()) {
            if ("setupOutboundProtocol".equals(m.getName()) && m.getParameterCount() == 1
                    && protocolInfoClass.isAssignableFrom(m.getParameterTypes()[0])) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException("Connection#setupOutboundProtocol(" + protocolInfoClass.getSimpleName() + ") not found");
    }

    private static Object createCommonListenerCookie(Class<?> commonCookieClass, Object gameProfile, Object clientInfo)
            throws ReflectiveOperationException {
        try {
            return commonCookieClass.getMethod("createInitial", gameProfile.getClass(), boolean.class)
                    .invoke(null, gameProfile, false);
        } catch (NoSuchMethodException ignored) {
            // Newer/older patches may include ClientInformation in createInitial signature.
        }
        for (Method m : commonCookieClass.getMethods()) {
            if (!m.getName().equals("createInitial")) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3 && p[0].isAssignableFrom(gameProfile.getClass()) && p[2] == boolean.class) {
                return m.invoke(null, gameProfile, clientInfo, false);
            }
        }
        throw new NoSuchMethodException("No compatible CommonListenerCookie#createInitial signature");
    }

    private static void placeNewPlayer(Object playerList, Object connection, Object serverPlayer, Object cookie)
            throws ReflectiveOperationException {
        try {
            tryInvokeInstance(playerList, "placeNewPlayer", connection, serverPlayer, cookie);
            return;
        } catch (NoSuchMethodException ignored) {
            // Older signatures may omit cookie.
        }
        tryInvokeInstance(playerList, "placeNewPlayer", connection, serverPlayer);
    }

    private static Object tryInvokeStatic(Class<?> type, String name, Object... args) throws ReflectiveOperationException {
        Method m = findCompatibleMethod(type, name, args);
        if (m == null) {
            throw new NoSuchMethodException(type.getName() + "#" + name);
        }
        return m.invoke(null, args);
    }

    private static Object tryInvokeInstance(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method m = findCompatibleMethod(target.getClass(), name, args);
        if (m == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        }
        return m.invoke(target, args);
    }

    /**
     * Force le ServerPlayer en SURVIVAL puis ecrit directement {@code Abilities.invulnerable=false}.
     *
     * <p>Deux champs d'invulnerabilite coexistent sur un joueur NMS : {@code Entity.invulnerable} (ce que
     * Bukkit {@code setInvulnerable()} ecrit) et {@code Abilities.invulnerable} (pilote par le gametype, lu
     * par {@code Player.isInvulnerableTo} et donc par {@code CraftLivingEntity.isInvulnerable()}). Un fake
     * player ne en CREATIVE/SPECTATOR herite de {@code Abilities.invulnerable=true} et reste intapable. On
     * change le gametype, puis on ecrit le champ directement en filet de securite car la maj d'abilities via
     * gamemode n'est pas fiable sur un fake player, et on pousse {@code onUpdateAbilities()}.
     */
    private static void forceSurvivalGameType(Object serverPlayer) {
        try {
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            @SuppressWarnings("unchecked")
            Object survival = Enum.valueOf((Class<? extends Enum>) gameTypeClass, "SURVIVAL");
            serverPlayer.getClass().getMethod("setGameMode", gameTypeClass).invoke(serverPlayer, survival);
        } catch (Throwable ignored) {
            // Mapping/fork sans setGameMode(GameType) accessible : on s'appuie sur le forcage direct ci-dessous.
        }
        try {
            Object abilities = serverPlayer.getClass().getMethod("getAbilities").invoke(serverPlayer);
            setBooleanFieldIfPresent(abilities, "invulnerable", false);
            serverPlayer.getClass().getMethod("onUpdateAbilities").invoke(serverPlayer);
        } catch (Throwable ignored) {
            // Best effort : le bot spawn meme si le forcage des abilities echoue.
        }
    }

    private static void setBooleanFieldIfPresent(Object target, String fieldName, boolean value) {
        if (target == null || fieldName == null || fieldName.isBlank()) {
            return;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var f = type.getDeclaredField(fieldName);
                if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    f.setAccessible(true);
                    f.setBoolean(target, value);
                }
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static void setFloatFieldIfPresent(Object target, String fieldName, float value) {
        if (target == null || fieldName == null || fieldName.isBlank()) {
            return;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var f = type.getDeclaredField(fieldName);
                if (f.getType() == float.class || f.getType() == Float.class) {
                    f.setAccessible(true);
                    f.setFloat(target, value);
                }
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object... args) {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length != args.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < params.length; i++) {
                if (!isCompatible(params[i], args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return m;
            }
        }
        return null;
    }

    private static boolean isCompatible(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        Class<?> boxed = box(parameterType);
        return boxed.isAssignableFrom(arg.getClass());
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}

