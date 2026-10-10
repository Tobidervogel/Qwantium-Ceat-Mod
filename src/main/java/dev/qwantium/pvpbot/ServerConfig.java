package dev.qwantium.pvpbot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Server side settings, stored in {@code config/qwantiumbot-server.properties}. */
public record ServerConfig(boolean allowBot) {
	private static final String FILE_NAME = "qwantiumbot-server.properties";
	private static final String KEY_ALLOW_BOT = "allow-bot";

	public static ServerConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		Properties properties = new Properties();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				properties.load(reader);
			} catch (IOException e) {
				QwantiumBotMod.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}

		ServerConfig config = new ServerConfig(Boolean.parseBoolean(properties.getProperty(KEY_ALLOW_BOT, "true")));
		properties.setProperty(KEY_ALLOW_BOT, Boolean.toString(config.allowBot()));
		try (Writer writer = Files.newBufferedWriter(path)) {
			properties.store(writer, "Qwantium PvP Bot - allow players with the mod to use the PvP bot on this server");
		} catch (IOException e) {
			QwantiumBotMod.LOGGER.warn("Could not write {}", path, e);
		}
		return config;
	}
}
