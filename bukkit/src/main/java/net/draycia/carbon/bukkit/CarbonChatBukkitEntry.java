package net.draycia.carbon.bukkit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import io.papermc.lib.PaperLib;
import java.util.logging.Level;
import net.draycia.carbon.bukkit.listeners.BukkitChatListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

@Singleton
public final class CarbonChatBukkitEntry extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 8720;

    private @MonotonicNonNull CarbonChatBukkit carbon;
    private Injector injector;

    @Override
    public void onLoad() {
        this.injector = Guice.createInjector(new CarbonChatBukkitModule(this,
            this.getDataFolder().toPath()));
        this.carbon = this.injector.getInstance(CarbonChatBukkit.class);
    }

    @Override
    public void onEnable() {
        if (!PaperLib.isPaper()) {
            this.getLogger().log(Level.SEVERE, "*");
            this.getLogger().log(Level.SEVERE, "* CarbonChat makes extensive use of APIs added by Paper.");
            this.getLogger().log(Level.SEVERE, "* For this reason, CarbonChat is not compatible with Spigot or CraftBukkit servers.");
            this.getLogger().log(Level.SEVERE, "* Upgrade your server to Paper in order to use CarbonChat.");
            this.getLogger().log(Level.SEVERE, "*");
            PaperLib.suggestPaper(this, Level.SEVERE);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        this.getServer().getPluginManager().registerEvents(
            this.injector.getInstance(BukkitChatListener.class), this);

        this.carbon.initialize();
    }

    public Injector injector() {
        return this.injector;
    }

    @Override
    public void onDisable() {
    }

}
