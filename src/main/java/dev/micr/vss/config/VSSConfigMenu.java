package dev.micr.vss.config;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.option.Range;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2561;
import net.minecraft.class_2960;

public class VSSConfigMenu implements ConfigEntryPoint {
   public void registerConfigLate(ConfigBuilder builder) {
      VSSClientConfig cfg = VSSClientConfig.CONFIG;
      StorageEventHandler save = cfg::save;
      String version = FabricLoader.getInstance().getModContainer("vss").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
      ModOptionsBuilder mod = builder.registerModOptions("vss", "Voxy Server Side", version).setIcon(class_2960.method_60654("vss:icon.png"));
      OptionPageBuilder page = builder.createOptionPage();
      page.setName(class_2561.method_43471("vss.config.page"));
      OptionGroupBuilder receiveGroup = builder.createOptionGroup();
      BooleanOptionBuilder receiveOption = builder.createBooleanOption(class_2960.method_60654("vss:receive_server_lods"));
      receiveOption.setName(class_2561.method_43471("vss.config.receive_server_lods"));
      receiveOption.setTooltip(class_2561.method_43471("vss.config.receive_server_lods.tooltip"));
      receiveOption.setImpact(OptionImpact.HIGH);
      receiveOption.setDefaultValue(true);
      receiveOption.setBinding(v -> cfg.receiveServerLods = v, () -> cfg.receiveServerLods);
      receiveOption.setStorageHandler(save);
      receiveGroup.addOption(receiveOption);
      page.addOptionGroup(receiveGroup);
      class_2960[] enabledDep = new class_2960[]{class_2960.method_60654("vss:receive_server_lods")};
      OptionGroupBuilder distanceGroup = builder.createOptionGroup();
      IntegerOptionBuilder distanceOption = builder.createIntegerOption(class_2960.method_60654("vss:lod_distance"));
      distanceOption.setName(class_2561.method_43471("vss.config.lod_distance"));
      distanceOption.setTooltip(class_2561.method_43471("vss.config.lod_distance.tooltip"));
      distanceOption.setDefaultValue(0);
      distanceOption.setRange(new Range(0, 512, 1));
      distanceOption.setValueFormatter(
         v -> v == 0 ? class_2561.method_43471("vss.config.lod_distance.server_default") : class_2561.method_43470(Integer.toString(v))
      );
      distanceOption.setBinding(v -> cfg.lodDistanceChunks = v, () -> cfg.lodDistanceChunks);
      distanceOption.setStorageHandler(save);
      distanceOption.setEnabledProvider(s -> s.readBooleanOption(enabledDep[0]), enabledDep);
      distanceGroup.addOption(distanceOption);
      page.addOptionGroup(distanceGroup);
      OptionGroupBuilder offThreadGroup = builder.createOptionGroup();
      BooleanOptionBuilder offThreadOption = builder.createBooleanOption(class_2960.method_60654("vss:off_thread_processing"));
      offThreadOption.setName(class_2561.method_43471("vss.config.off_thread_processing"));
      offThreadOption.setTooltip(class_2561.method_43471("vss.config.off_thread_processing.tooltip"));
      offThreadOption.setDefaultValue(true);
      offThreadOption.setBinding(v -> cfg.offThreadSectionProcessing = v, () -> cfg.offThreadSectionProcessing);
      offThreadOption.setStorageHandler(save);
      offThreadOption.setEnabledProvider(s -> s.readBooleanOption(enabledDep[0]), enabledDep);
      offThreadGroup.addOption(offThreadOption);
      page.addOptionGroup(offThreadGroup);
      mod.addPage(page);
   }
}
