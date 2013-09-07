package au.com.mineauz.MobHunting.achievements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import au.com.mineauz.MobHunting.Messages;
import au.com.mineauz.MobHunting.MobHunting;
import au.com.mineauz.MobHunting.storage.AchievementStore;
import au.com.mineauz.MobHunting.storage.DataCallback;

public class AchievementManager implements Listener
{
	private HashMap<String, Achievement> mAchievements = new HashMap<String, Achievement>();
	
	public void initialize()
	{
		Bukkit.getPluginManager().registerEvents(this, MobHunting.instance);
	}
	
	public Achievement getAchievement(String id)
	{
		if(!mAchievements.containsKey(id))
			throw new IllegalArgumentException("There is no achievement by the id: " + id); //$NON-NLS-1$
		
		return mAchievements.get(id);
	}
	
	public void registerAchievement(Achievement achievement)
	{
		Validate.notNull(achievement);
		
		if(achievement instanceof ProgressAchievement)
		{
			if(((ProgressAchievement)achievement).inheritFrom() != null)
			{
				Validate.isTrue(mAchievements.containsKey(((ProgressAchievement)achievement).inheritFrom()));
				Validate.isTrue(mAchievements.get(((ProgressAchievement)achievement).inheritFrom()) instanceof ProgressAchievement);
			}
		}
		
		mAchievements.put(achievement.getID(), achievement);
		
		if(achievement instanceof Listener)
			Bukkit.getPluginManager().registerEvents((Listener)achievement, MobHunting.instance);
	}
	
	public boolean hasAchievement(String achievement, Player player)
	{
		return hasAchievement(getAchievement(achievement), player);
	}
	public boolean hasAchievement(Achievement achievement, Player player)
	{
		if(!player.hasMetadata("MH:achievement-" + achievement.getID())) //$NON-NLS-1$
			return false;
		
		for(MetadataValue value : player.getMetadata("MH:achievement-" + achievement.getID())) //$NON-NLS-1$
		{
			if(value.getOwningPlugin() == MobHunting.instance)
			{
				if(value.value() instanceof Boolean)
					return value.asBoolean();
				
				return false;
			}
		}
		
		return false;
	}
	
	public boolean achievementsEnabledFor(Player player)
	{
		if(!player.hasMetadata("MH:achievements")) //$NON-NLS-1$
			return true;
		
		for(MetadataValue value : player.getMetadata("MH:achievements")) //$NON-NLS-1$
		{
			if(value.getOwningPlugin() == MobHunting.instance)
			{
				if(value.value() instanceof Boolean)
					return value.asBoolean();
				
				return false;
			}
		}
		
		return true;
	}
	
	public int getProgress(String achievement, Player player)
	{
		Achievement a = getAchievement(achievement);
		Validate.isTrue(a instanceof ProgressAchievement, "This achievement does not have progress"); //$NON-NLS-1$
		
		return getProgress((ProgressAchievement)a, player);
	}
	
	public int getProgress(ProgressAchievement achievement, Player player)
	{
		if(!player.hasMetadata("MH:achievement-" + achievement.getID())) //$NON-NLS-1$
			return 0;
		
		for(MetadataValue value : player.getMetadata("MH:achievement-" + achievement.getID())) //$NON-NLS-1$
		{
			if(value.getOwningPlugin() == MobHunting.instance)
			{
				if(value.value() instanceof Boolean)
					return (value.asBoolean() ? achievement.getMaxProgress() : 0);
				else if(value.value() instanceof Integer)
					return value.asInt();
				
				return 0;
			}
		}
		
		return 0;
	}
	
	public void requestCompletedAchievements(OfflinePlayer player, final DataCallback<List<Map.Entry<Achievement, Integer>>> callback)
	{
		// Look through the metadata for online players
		if(player.isOnline())
		{
			List<Map.Entry<Achievement, Integer>> achievements = new ArrayList<Map.Entry<Achievement, Integer>>();
			ArrayList<Map.Entry<Achievement, Integer>> toRemove = new ArrayList<Map.Entry<Achievement,Integer>>();
			
			for(Achievement achievement : mAchievements.values())
			{
				if(hasAchievement(achievement, player.getPlayer()))
				{
					achievements.add(new AbstractMap.SimpleImmutableEntry<Achievement, Integer>(achievement, -1));
					
					// If the achievement is a higher level, remove the lower level from the list
					if(achievement instanceof ProgressAchievement && ((ProgressAchievement)achievement).inheritFrom() != null)
						toRemove.add(new AbstractMap.SimpleImmutableEntry<Achievement, Integer>(getAchievement(((ProgressAchievement)achievement).inheritFrom()), -1));
				}
				else if(achievement instanceof ProgressAchievement && getProgress((ProgressAchievement)achievement, player.getPlayer()) > 0)
					achievements.add(new AbstractMap.SimpleImmutableEntry<Achievement, Integer>(achievement, getProgress((ProgressAchievement)achievement, player.getPlayer())));
			}
			
			achievements.removeAll(toRemove);
			
			callback.onCompleted(achievements);
			return;
		}
		
		// Look through the data store for offline players
		MobHunting.instance.getDataStore().requestAllAchievements(player, new DataCallback<Set<AchievementStore>>()
		{
			@Override
			public void onError( Throwable error )
			{
				callback.onError(error);
			}
			
			@Override
			public void onCompleted( Set<AchievementStore> data )
			{
				List<Map.Entry<Achievement, Integer>> achievements = new ArrayList<Map.Entry<Achievement, Integer>>();
				ArrayList<Map.Entry<Achievement, Integer>> toRemove = new ArrayList<Map.Entry<Achievement,Integer>>();
				
				
				for(AchievementStore stored : data)
				{
					if(mAchievements.containsKey(stored.id))
					{
						Achievement achievement = mAchievements.get(stored.id);
						achievements.add(new AbstractMap.SimpleImmutableEntry<Achievement, Integer>(achievement, stored.progress));
						
						// If the achievement is a higher level, remove the lower level from the list
						if(stored.progress == -1 && achievement instanceof ProgressAchievement && ((ProgressAchievement)achievement).inheritFrom() != null)
							toRemove.add(new AbstractMap.SimpleImmutableEntry<Achievement, Integer>(getAchievement(((ProgressAchievement)achievement).inheritFrom()), -1));
					}
				}
				
				achievements.removeAll(toRemove);
				
				callback.onCompleted(achievements);
			}
		});
	}
	
	public Collection<Achievement> getAllAchievements()
	{
		return Collections.unmodifiableCollection(mAchievements.values());
	}
	
	public void awardAchievement(String achievement, Player player)
	{
		awardAchievement(getAchievement(achievement), player);
	}
	private void broadcast(String message, Player except)
	{
		for(Player player : Bukkit.getOnlinePlayers())
		{
			if(player.equals(except))
				continue;
			
			player.sendMessage(message);
		}
	}
	public void awardAchievement(Achievement achievement, Player player)
	{
		if(!achievementsEnabledFor(player) || hasAchievement(achievement, player))
			return;
	
		MobHunting.instance.getDataStore().recordAchievement(player, achievement);

		player.setMetadata("MH:achievement-" + achievement.getID(), new FixedMetadataValue(MobHunting.instance, true)); //$NON-NLS-1$
		
		if(MobHunting.config().broadcastAchievement && (!(achievement instanceof TheHuntBegins) || MobHunting.config().broadcastFirstAchievement))
		{
			player.sendMessage(ChatColor.GOLD + Messages.getString("mobhunting.achievement.awarded", "name", "" + ChatColor.WHITE + ChatColor.ITALIC + achievement.getName())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + achievement.getDescription()); //$NON-NLS-1$
			player.sendMessage(ChatColor.WHITE + "" + ChatColor.ITALIC + Messages.getString("mobhunting.achievement.awarded.prize", "prize", MobHunting.getEconomy().format(achievement.getPrize()))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			
			broadcast(ChatColor.GOLD + Messages.getString("mobhunting.achievement.awarded.broadcast", "player", player.getName(), "name", "" + ChatColor.WHITE + ChatColor.ITALIC + achievement.getName()), player); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		
		player.getWorld().playSound(player.getLocation(), Sound.LEVEL_UP, 1.0f, 1.0f);
		FireworkEffect effect = FireworkEffect.builder().withColor(Color.ORANGE, Color.YELLOW).flicker(true).trail(false).build();
		Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
		FireworkMeta meta = firework.getFireworkMeta();
		meta.setPower(1);
		meta.addEffect(effect);
		firework.setFireworkMeta(meta);
		
	}
	
	public void awardAchievementProgress(String achievement, Player player, int amount)
	{
		Achievement a = getAchievement(achievement);
		Validate.isTrue(a instanceof ProgressAchievement, "You need to award normal achievements with awardAchievement()"); //$NON-NLS-1$
		
		awardAchievementProgress((ProgressAchievement)a, player, amount);
	}
	
	public void awardAchievementProgress(ProgressAchievement achievement, Player player, int amount)
	{
		if(!achievementsEnabledFor(player) || hasAchievement(achievement, player))
			return;
		
		Validate.isTrue(amount > 0);
		
		int curProgress = getProgress(achievement, player);
		
		while(achievement.inheritFrom() != null && curProgress == 0)
		{
			// This allows us to just mark progress against the highest level version and have it automatically given to the lower level ones
			if(!hasAchievement(achievement.inheritFrom(), player))
			{
				achievement = (ProgressAchievement)getAchievement(achievement.inheritFrom());
				curProgress = getProgress(achievement, player);
			}
			else
			{
				curProgress = ((ProgressAchievement)getAchievement(achievement.inheritFrom())).getMaxProgress();
			}
		}

		int maxProgress = achievement.getMaxProgress();
		int nextProgress = Math.min(maxProgress, curProgress + amount);
		
		if(nextProgress == maxProgress)
			awardAchievement(achievement, player);
		else
		{
			player.setMetadata("MH:achievement-" + achievement.getID(), new FixedMetadataValue(MobHunting.instance, nextProgress)); //$NON-NLS-1$
			
			MobHunting.instance.getDataStore().recordAchievementProgress(player, achievement, nextProgress);
			
			int segment = Math.min(25, maxProgress / 2);
			
			if(curProgress / segment < nextProgress / segment || curProgress == 0 && nextProgress > 0)
			{
				player.sendMessage(ChatColor.BLUE + Messages.getString("mobhunting.achievement.progress", "name", "" + ChatColor.WHITE + ChatColor.ITALIC + achievement.getName())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				player.sendMessage(ChatColor.GRAY + "" + nextProgress + " / " + maxProgress); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}
	
	@SuppressWarnings( "unchecked" )
	public boolean upgradeAchievements()
	{
		File file = new File(MobHunting.instance.getDataFolder(), "awards.yml"); //$NON-NLS-1$

		if(!file.exists())
			return false;
		
		MobHunting.instance.getLogger().info("Upgrading old awards.yml file");
		
		YamlConfiguration config = new YamlConfiguration();
		try
		{
			config.load(file);

			for(String player : config.getKeys(false))
			{
				if(config.isList(player))
				{
					for(Object obj : (List<Object>)config.getList(player))
					{
						if(obj instanceof String)
						{
							MobHunting.instance.getDataStore().recordAchievement(Bukkit.getOfflinePlayer(player), getAchievement((String)obj));
						}
						else if(obj instanceof Map)
						{
							Map<String, Integer> map = (Map<String, Integer>)obj;
							String id = map.keySet().iterator().next();
							MobHunting.instance.getDataStore().recordAchievementProgress(Bukkit.getOfflinePlayer(player), (ProgressAchievement)getAchievement(id), (Integer)map.get(id));
						}
					}
				}
			}
			
			Files.delete(file.toPath());
			
			return true;
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		catch ( InvalidConfigurationException e )
		{
			e.printStackTrace();
		}

		return false;
	}
	
	public void load(final Player player)
	{
		player.setMetadata("MH:achievements", new FixedMetadataValue(MobHunting.instance, false));
		MobHunting.instance.getDataStore().requestAllAchievements(player, new DataCallback<Set<AchievementStore>>()
		{
			@Override
			public void onError( Throwable error )
			{
				error.printStackTrace();
				player.sendMessage(ChatColor.RED + "[WARNING] " + ChatColor.WHITE + "Your achievements failed to load. You will be unabled to get any until this is fixed.");
				player.setMetadata("MH:achievements", new FixedMetadataValue(MobHunting.instance, false));
			}
			
			@Override
			public void onCompleted( Set<AchievementStore> data )
			{
				// Load them up into metadata
				for(AchievementStore achievement : data)
				{
					if(achievement.progress == -1)
						player.setMetadata("MH:achievement-" + achievement.id, new FixedMetadataValue(MobHunting.instance, true)); //$NON-NLS-1$
					else
						player.setMetadata("MH:achievement-" + achievement.id, new FixedMetadataValue(MobHunting.instance, achievement.progress)); //$NON-NLS-1$
				}
				
				player.setMetadata("MH:achievements", new FixedMetadataValue(MobHunting.instance, true));
			}
		});
	}
	
	@EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
	private void onPlayerJoin(PlayerLoginEvent event)
	{
		load(event.getPlayer());
		
	}
}
