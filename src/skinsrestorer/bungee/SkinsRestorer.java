/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package skinsrestorer.bungee;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import skinsrestorer.bungee.commands.AdminCommands;
import skinsrestorer.bungee.commands.PlayerCommands;
import skinsrestorer.bungee.listeners.LoginListener;
import skinsrestorer.bungee.listeners.MessageListener;
import skinsrestorer.bungee.metrics.Metrics;
import skinsrestorer.shared.api.SkinsRestorerAPI;
import skinsrestorer.shared.format.SkinProfile;
import skinsrestorer.shared.storage.ConfigStorage;
import skinsrestorer.shared.storage.CooldownStorage;
import skinsrestorer.shared.storage.LocaleStorage;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.Factory;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.MySQL;
import skinsrestorer.shared.utils.SkinFetchUtils.SkinFetchFailedException;
import skinsrestorer.shared.utils.Updater;

public class SkinsRestorer extends Plugin {

	private static SkinsRestorer instance;

	public static SkinsRestorer getInstance() {
		return instance;
	}

	private Logger log;
	private Updater updater;
	private Factory factory;
	private boolean autoIn = false;
	private MySQL mysql;
	private File debug;

	public void logInfo(String message) {
		log.info(message);
	}

	@Override
	public void onEnable() {
		instance = this;
		log = getLogger();
		getDataFolder().mkdirs();
		ConfigStorage.getInstance().init(this.getResourceAsStream("config.yml"), false);
		LocaleStorage.getInstance().init(this.getResourceAsStream("messages.yml"), false);
		if (ConfigStorage.getInstance().UPDATE_CHECK == true) {
			updater = new Updater(getDescription().getVersion());
			updater.checkUpdates();
		} else {
			log.info(ChatColor.RED + "SkinsRestorer Updater is Disabled!");
			updater = null;
		}
		if (getProxy().getPluginManager().getPlugin("AutoIn") != null) {
			log.info(ChatColor.GREEN + "SkinsRestorer has detected that you are using AutoIn.");
			log.info(ChatColor.GREEN + "Check the USE_AUTOIN_SKINS option in your config!");
			autoIn = true;
		}

		if (ConfigStorage.getInstance().USE_MYSQL)
			SkinStorage.init(mysql = new MySQL(ConfigStorage.getInstance().MYSQL_HOST,
					ConfigStorage.getInstance().MYSQL_PORT, ConfigStorage.getInstance().MYSQL_DATABASE,
					ConfigStorage.getInstance().MYSQL_USERNAME, ConfigStorage.getInstance().MYSQL_PASSWORD));
		else {
			SkinStorage.init();
			this.getProxy().getScheduler().schedule(this, CooldownStorage.cleanupCooldowns, 0, 1, TimeUnit.MINUTES);
		}

		try {
			Class<?> factory = Class.forName("skinsrestorer.bungee.SkinFactoryBungee");
			this.factory = (Factory) factory.newInstance();
			log.info("[SkinsRestorer] Loaded Skin Factory for Bungeecord");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			getProxy().getPluginManager().unregisterListeners(this);
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		this.getProxy().getPluginManager().registerListener(this, new LoginListener());
		this.getProxy().getPluginManager().registerListener(this, new MessageListener());
		this.getProxy().getPluginManager().registerCommand(this, new AdminCommands());
		this.getProxy().getPluginManager().registerCommand(this, new PlayerCommands());
		this.getProxy().registerChannel("SkinUpdate");

		if (updater != null) {
			if (Updater.updateAvailable()) {
				log.info(ChatColor.DARK_GREEN + "==============================================");
				log.info(ChatColor.YELLOW + "  SkinsRestorer Updater  ");
				log.info(ChatColor.YELLOW + " ");
				log.info(ChatColor.GREEN + "    An update for SkinsRestorer has been found!");
				log.info(ChatColor.AQUA + "    SkinsRestorer " + ChatColor.GREEN + "v" + Updater.getHighest());
				log.info(ChatColor.AQUA + "    You are running " + ChatColor.RED + "v" + getDescription().getVersion());
				log.info(ChatColor.YELLOW + " ");
				log.info(ChatColor.YELLOW + "    Download at" + ChatColor.GREEN
						+ " https://www.spigotmc.org/resources/skinsrestorer.2124/");
				log.info(ChatColor.DARK_GREEN + "==============================================");
			} else {
				log.info(ChatColor.DARK_GREEN + "==============================================");
				log.info(ChatColor.YELLOW + "  SkinsRestorer Updater");
				log.info(ChatColor.YELLOW + " ");
				log.info(ChatColor.AQUA + "    You are running " + "v" + ChatColor.GREEN
						+ getDescription().getVersion());
				log.info(ChatColor.GREEN + "    The latest version of SkinsRestorer!");
				log.info(ChatColor.YELLOW + " ");
				log.info(ChatColor.DARK_GREEN + "==============================================");
			}
		}

		if (ConfigStorage.getInstance().DEBUG_ENABLED) {

			debug = new File(getDataFolder(), "debug.txt");

			PrintWriter out = null;

			try {
				debug.createNewFile();
				out = new PrintWriter(new FileOutputStream(debug), true);
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			try {

				out.println("Java version: " + System.getProperty("java.version"));
				out.println("Bungee version: " + ProxyServer.getInstance().getVersion());
				out.println("SkinsRestoerer version: " + getDescription().getVersion());
				out.println();

				String plugins = "";
				for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins())
					plugins += plugin.getDescription().getName() + " (" + plugin.getDescription().getVersion() + "), ";

				out.println("Plugin list: " + plugins);
				out.println();
				out.println("Property output from MojangAPI (Notch) : ");

				SkinProfile sp = MojangAPI.getSkinProfile(MojangAPI.getProfile("Notch").getId(), "Notch");

				out.println("Name: " + sp.getSkinProperty().getName());
				out.println("Value: " + sp.getSkinProperty().getValue());
				out.println("Signature: " + sp.getSkinProperty().getSignature());
				out.println();

				out.println("Raw data from MojangAPI (Blackfire62): ");
				Method m = MojangAPI.class.getDeclaredMethod("readURL", URL.class);

				m.setAccessible(true);
				String output = (String) m.invoke(null,
						new URL("https://sessionserver.mojang.com/session/minecraft/profile/"
								+ MojangAPI.getProfile("Blackfire62").getId() + "?unsigned=false"));

				out.println(output);

				out.println("\n\n\n\n\n\n\n\n\n\n");

			} catch (Exception e) {
				out.println("=========================================");
				e.printStackTrace(out);
				out.println("=========================================");
			}

			log.info(
					ChatColor.RED + "[SkinsRestorer] Debug file crated! Automatically setting debug mode to false... ");
			ConfigStorage.getInstance().config.set("Debug Enabled", false);
			ConfigStorage.getInstance().config.save();
			log.info(ChatColor.RED
					+ "[SkinsRestorer] Please check the contents of the file and send the contents to developers, if you are experiencing problems!");
			log.info(ChatColor.RED + "[SkinsRestorer] URL for error reporting: " + ChatColor.YELLOW
					+ "https://github.com/Th3Tr0LLeR/SkinsRestorer---Maro/issues");

		}

		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e) {
			log.info(ChatColor.RED + "Failed to start Metrics.");
		}
	}

	@Override
	public void onDisable() {
		instance = null;
	}

	@Deprecated
	public void setSkin(final String playerName, final String skinName) throws SkinFetchFailedException {
		SkinsRestorerAPI.setSkin(playerName, skinName);
	}

	@Deprecated
	public boolean hasSkin(String playerName) {
		return SkinsRestorerAPI.hasSkin(playerName);
	}

	public com.gmail.bartlomiejkmazur.autoin.api.AutoInAPI getAutoInAPI() {
		return com.gmail.bartlomiejkmazur.autoin.api.APICore.getAPI();
	}

	public Factory getFactory() {
		return factory;
	}

	public String getVersion() {
		return getDescription().getVersion();
	}

	public boolean isAutoInEnabled() {
		return autoIn;
	}

	public MySQL getMySQL() {
		return mysql;
	}
}
