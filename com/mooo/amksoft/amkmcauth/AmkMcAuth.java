package com.mooo.amksoft.amkmcauth;

import com.google.common.base.Charsets;
import com.google.common.io.PatternFilenameFilter;
import com.mooo.amksoft.amkmcauth.commands.CmdChangePassword;
import com.mooo.amksoft.amkmcauth.commands.CmdLogin;
import com.mooo.amksoft.amkmcauth.commands.CmdLogout;
import com.mooo.amksoft.amkmcauth.commands.CmdRegister;
import com.mooo.amksoft.amkmcauth.commands.CmdAmkAuth;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

// To publisch JavaCode on bukkit.org:
// Use these tags: [syntax=java]code here[/syntax]

public class AmkMcAuth extends JavaPlugin {

    public static File dataFolder;
    public Config c;
    public Logger log;
    public LogFilter MyFilter; 

    /**
     * Registers a command in the server. If the command isn't defined in plugin.yml
     * the NPE is caught, and a warning line is sent to the console.
     *
     * @param ce      CommandExecutor to be registered
     * @param command Command name as specified in plugin.yml
     * @param jp      Plugin to register under
     */
    private void registerCommand(CommandExecutor ce, String command, JavaPlugin jp) {
        try {
            jp.getCommand(command).setExecutor(ce);
        } catch (NullPointerException e) {
            jp.getLogger().warning(String.format(Language.COULD_NOT_REGISTER_COMMAND.toString(), command, e.getMessage()));
        }
    }

    private void update() {
        final File userdataFolder = new File(dataFolder, "userdata");
        if (!userdataFolder.exists() || !userdataFolder.isDirectory()) return;
        for (String fileName : userdataFolder.list(new PatternFilenameFilter("(?i)^.+\\.yml$"))) {
            String playerName = fileName.substring(0, fileName.length() - 4); // ".yml" = 4
            try {
                //noinspection ResultOfMethodCallIgnored
                UUID.fromString(playerName);
                continue; // FileName(PlayerName) sounds like a UUID, no need to rename!!
            } catch (IllegalArgumentException ignored) {}

            boolean Online=true;
            UUID u;
            
        	if(Bukkit.getOnlineMode()!= Online) 
    		{
        	    // Server runs 'OffLine' AmkMcAuth calculates the UUID for this player...
        	    u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(Charsets.UTF_8));    		
    		}
        	else
        		{            		
        		try {
        			u = AmkAUtils.getUUID(playerName); // Get the official MOJANG UUID!
        		} catch (Exception ex) {
        			// Oops, its not a MOJANG UUID, (or server is running in 'OffLine' mode)!!  
        			u = this.getServer().getOfflinePlayer(playerName).getUniqueId();
        		}	
        		if (u == null) {
        			this.getLogger().warning(Language.ERROR.toString());
        			continue;
        		}
        	}

            // Original FileRename Code does not work, this one (below) works
     		File origFile = new File(userdataFolder.toString() + File.separator + fileName);		
       		File destFile = new File(userdataFolder.toString() + File.separator + u + ".yml");
            if (!origFile.exists()){
                this.getLogger().info("File " + origFile.toString() + " bestaat NIET??");
            }
            if (destFile.exists()){
                this.getLogger().info("File " + destFile.toString() + " bestaat AL??");
            }
            if (origFile.renameTo(destFile)) {
                this.getLogger().info(String.format(Language.CONVERTED_USERDATA.toString(), fileName, u + ".yml"));
            } else {
                 this.getLogger().warning(String.format(Language.COULD_NOT_CONVERT_USERDATA.toString(), fileName, u + ".yml"));
            }
                
        }
    }

    private void saveLangFile(String name) {
        if (!new File(this.getDataFolder() + File.separator + "lang" + File.separator + name + ".properties").exists())
            this.saveResource("lang" + File.separator + name + ".properties", false);
    }

    @Override
    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(this);

        for (Player p : this.getServer().getOnlinePlayers()) {
            AuthPlayer ap = AuthPlayer.getAuthPlayer(p);
            if (ap.isLoggedIn()) ap.logout(this, false);
        }

        PConfManager.saveAllManagers();
        PConfManager.purge();
    }

    @Override
    public void onEnable() {
        AmkMcAuth.dataFolder = this.getDataFolder();

        if (!new File(getDataFolder(), "config.yml").exists()) this.saveDefaultConfig();

        this.c = new Config(this); // Is deze nodig ??, 'c' wordt niet gebruikt??
        this.log = this.getLogger();

        //http://stackoverflow.com/questions/17555601/suppress-console-output-of-player-commands
        //CustomFilter filter = new CustomFilter();
        //plugin.getServer().getLogger().setFilter(filter);
        //Where plugin is the instance of the plugin in the main class
    
        LogFilter MyFilter = new LogFilter();
        //this.getLogger().setFilter(MyFilter);
        this.getServer().getLogger().setFilter(MyFilter);

        //this.log.setFilter(new LogFilter()); 

		//this.getServer().getLogger().setFilter(MyFilter);
		//this.getLogger().setFilter(MyFilter);
		//log.getLogger("Minecraft").setFilter(MyFilter);

		//for (Plugin p : getServer().getPluginManager().getPlugins()) {
		//	pname = p.toString();
		//	p.getLogger().setFilter(then.masterFilter);
		//}
		//this.getServer().getLogger().setFilter(masterFilter);
		//Bukkit.getLogger().setFilter(masterFilter);
		//Logger.getLogger("Minecraft").setFilter(masterFilter);
		
		
        this.saveLangFile("en_us");

        try {
            new Language.LanguageHelper(new File(this.getDataFolder(), this.getConfig().getString("general.language_file", "lang/en_us.properties")));
        } catch (IOException e) {
            this.log.severe("Could not load language file: " + e.getMessage());
            this.log.severe("Disabling plugin.");
            this.setEnabled(false);
            return;
        }

        if (Config.checkOldUserdata) this.update();

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new AuthListener(this), this);
        
        this.registerCommand(new CmdAmkAuth(this), "amkmcauth", this);
        this.registerCommand(new CmdLogin(this), "login", this);
        this.registerCommand(new CmdLogout(this), "logout", this);
        this.registerCommand(new CmdRegister(this), "register", this);
        this.registerCommand(new CmdChangePassword(), "changepassword", this);

    	if (getConfig().getBoolean("general.metrics_enabled"))
    	{
    		//-- Hidendra's Metrics --//
    		try {
    			Metrics metrics = new Metrics(this);
    			if (!metrics.start()) this.getLogger().info(Language.METRICS_OFF.toString());
    			else this.getLogger().info(Language.METRICS_ENABLED.toString());
    		} catch (Exception ignore) {
    			// Failed to submit the stats :-(    			
    			this.getLogger().warning(Language.COULD_NOT_START_METRICS.toString());
    		}
    	}
        else
        {
        	this.getLogger().info("Metrics on plugin disabled.");
        }

        for (Player p : this.getServer().getOnlinePlayers()) {
            AuthPlayer ap = AuthPlayer.getAuthPlayer(p);
            if (ap.isLoggedIn()) continue;
            if (ap.isRegistered()) ap.createLoginReminder(this);
            else ap.createRegisterReminder(this);
        }

        this.log.info(this.getDescription().getName() + " v" + this.getDescription().getVersion() + " " + Language.ENABLED + ".");
    }

}