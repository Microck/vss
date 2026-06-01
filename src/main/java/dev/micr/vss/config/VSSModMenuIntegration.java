package dev.micr.vss.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;

public class VSSModMenuIntegration implements ModMenuApi {
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return parent -> {
         try {
            ModOptions page = ConfigManager.CONFIG.getModOptions().stream().filter(a -> a.configId().equals("vss")).findFirst().orElse(null);
            return page == null ? null : VideoSettingsScreen.createScreen(parent, (OptionPage)page.pages().get(0));
         } catch (Throwable e) {
            return null;
         }
      };
   }
}
