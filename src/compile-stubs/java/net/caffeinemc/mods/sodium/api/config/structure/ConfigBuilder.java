package net.caffeinemc.mods.sodium.api.config.structure;
import net.minecraft.class_2960;
public class ConfigBuilder { public ModOptionsBuilder registerModOptions(String id,String name,String version){return new ModOptionsBuilder();} public OptionPageBuilder createOptionPage(){return new OptionPageBuilder();} public OptionGroupBuilder createOptionGroup(){return new OptionGroupBuilder();} public BooleanOptionBuilder createBooleanOption(class_2960 id){return new BooleanOptionBuilder();} public IntegerOptionBuilder createIntegerOption(class_2960 id){return new IntegerOptionBuilder();} }
