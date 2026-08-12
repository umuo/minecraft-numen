package com.dwinovo.numen.core.assist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 世界存档内的协助模式设置。 */
public final class AssistRegistry extends SavedData {

    private static final String DATA_NAME = "numen_assist";

    private static final Codec<AssistConfig> CONFIG_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(AssistConfig::enabled),
            Codec.BOOL.optionalFieldOf("mining", true).forGetter(AssistConfig::mining),
            Codec.BOOL.optionalFieldOf("combat", true).forGetter(AssistConfig::combat),
            Codec.BOOL.optionalFieldOf("clearing", true).forGetter(AssistConfig::clearing),
            Codec.INT.optionalFieldOf("radius", 10).forGetter(AssistConfig::radius),
            Codec.INT.optionalFieldOf("keepDistance", 4).forGetter(AssistConfig::keepDistance)
    ).apply(i, AssistConfig::new));

    private static final Codec<AssistRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, CONFIG_CODEC)
                    .optionalFieldOf("companions", Map.of()).forGetter(r -> r.configs)
    ).apply(i, AssistRegistry::new));

    private static final SavedData.Factory<AssistRegistry> FACTORY = new SavedData.Factory<>(
            AssistRegistry::new, AssistRegistry::load,
            net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private final Map<UUID, AssistConfig> configs;

    AssistRegistry() {
        this.configs = new HashMap<>();
    }

    private AssistRegistry(Map<UUID, AssistConfig> loaded) {
        this.configs = new HashMap<>(loaded);
    }

    public static AssistRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public AssistConfig get(UUID companion) {
        return configs.getOrDefault(companion, AssistConfig.DEFAULT);
    }

    public void put(UUID companion, AssistConfig config) {
        if (config.equals(AssistConfig.DEFAULT)) {
            configs.remove(companion);
        } else {
            configs.put(companion, config);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, this).result().ifPresent(encoded -> {
            if (encoded instanceof CompoundTag compound) {
                tag.merge(compound);
            }
        });
        return tag;
    }

    static AssistRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElseGet(AssistRegistry::new);
    }
}
