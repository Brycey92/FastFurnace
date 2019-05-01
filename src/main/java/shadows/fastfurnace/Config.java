package shadows.fastfurnace;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class Config {
    private static Configuration config;
    private static File file;

    private static boolean lenientOutputMatching;
    private static boolean reconstructFurnaceRecipeOutputs;

    public static void init(FMLPreInitializationEvent event) {
        file = event.getSuggestedConfigurationFile();
        config = new Configuration(file);
        try {
            config.load();
            config.setCategoryComment(Configuration.CATEGORY_GENERAL, "These options are useful to prevent furnace outputs not stacking past 1 if there's a mod that alters all items. For example, CustomNPCs adds an NBT tag to all items. You should only need to change one of these to true to true, but they may cause unwanted side effects.");
            lenientOutputMatching = config.getBoolean("lenientOutputMatching", Configuration.CATEGORY_GENERAL, false, "If false, use strict matching and NBT and metadata between furnace recipe output and what's in a furnace's output slot. Original Forge behavior is true.");
            reconstructFurnaceRecipeOutputs = config.getBoolean("reconstructFurnaceRecipeOutputs", Configuration.CATEGORY_GENERAL, false, "Whether to reconstruct the game's furnace recipe outputs once the game has initialized.");
        } catch (Exception e) {
            FastFurnace.LOG.error("Cannot load configuration file!", e);
        } finally {
            config.save();
        }
    }

    public static boolean usingLenientOutputMatching() {
        return lenientOutputMatching;
    }

    public static boolean shouldReconstructFurnaceRecipeOutputs() {
        return reconstructFurnaceRecipeOutputs;
    }
}