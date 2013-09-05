package au.com.mineauz.MobHunting.storage;

import java.util.Map;
import org.bukkit.entity.Player;

import au.com.mineauz.MobHunting.ExtendedMobType;
import au.com.mineauz.MobHunting.achievements.Achievement;
import au.com.mineauz.MobHunting.achievements.ProgressAchievement;

public interface DataStore
{
	public void initialize() throws DataStoreException;
	
	public void shutdown() throws DataStoreException;
	
	
	public void recordKill(Player player, ExtendedMobType type) throws DataStoreException;
	
	public void recordAssist(Player player, Player killer, ExtendedMobType type) throws DataStoreException;
	
	public void recordAchievement(Player player, Achievement achievement) throws DataStoreException;
	
	public void recordAchievementProgress(Player player, ProgressAchievement achievement, int progress) throws DataStoreException;
	
	
	public Map<String, Integer> loadAchievements(Player player) throws DataStoreException;
}
