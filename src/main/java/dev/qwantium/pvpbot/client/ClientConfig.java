package dev.qwantium.pvpbot.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.qwantium.pvpbot.QwantiumBotMod;
import dev.qwantium.pvpbot.client.bot.Difficulty;
import dev.qwantium.pvpbot.client.bot.TargetMode;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Client settings, stored in {@code config/qwantiumbot-client.json}. */
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("qwantiumbot-client.json");

	public Difficulty difficulty = Difficulty.NORMAL;
	public TargetMode targetMode = TargetMode.NEAREST_PLAYER;
	/** Player name used when {@link #targetMode} is {@link TargetMode#PLAYER}. */
	public String targetName = "";
	public boolean autoRespawn = true;
	/** Blocks around you in which the bot looks for opponents. */
	public double searchRange = 24.0;

	public static ClientConfig load() {
		ClientConfig config = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				config = GSON.fromJson(reader, ClientConfig.class);
			} catch (IOException | JsonParseException e) {
				QwantiumBotMod.LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}

		if (config == null) {
			config = new ClientConfig();
		}
		config.sanitize();
		config.save();
		return config;
	}

	public void save() {
		try (Writer writer = Files.newBufferedWriter(PATH)) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			QwantiumBotMod.LOGGER.warn("Could not write {}", PATH, e);
		}
	}

	private void sanitize() {
		if (this.difficulty == null) {
			this.difficulty = Difficulty.NORMAL;
		}
		if (this.targetMode == null) {
			this.targetMode = TargetMode.NEAREST_PLAYER;
		}
		if (this.targetName == null) {
			this.targetName = "";
		}
		if (!(this.searchRange >= 4.0 && this.searchRange <= 64.0)) {
			this.searchRange = 24.0;
		}
	}
}
