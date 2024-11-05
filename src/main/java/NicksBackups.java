package dev.nicks;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class NicksBackups extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String BACKUP_FOLDER = "/home/mcserver/mc_server/backupsPlugin/normal";
    private static final String FORCED_BACKUP_FOLDER = "/home/mcserver/mc_server/backupsPlugin/forced";

    @Override
    public void onEnable() {
        this.getCommand("load-backup").setExecutor(this);
        this.getCommand("load-backup").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("load-backup")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /load-backup <normal|forced> <backup_name>");
                return false;
            }

            String backupType = args[0].toLowerCase();
            String backupName = args[1];
            
            if (!backupType.equals("normal") && !backupType.equals("forced")) {
                sender.sendMessage("Invalid backup type. Use 'normal' or 'forced'");
                return false;
            }

            String backupPath = backupType.equals("normal") ? BACKUP_FOLDER : FORCED_BACKUP_FOLDER;
            File backupDir = new File(backupPath, backupName);

            if (!backupDir.exists()) {
                sender.sendMessage("Backup not found: " + backupName);
                return false;
            }

            deleteCurrentWorlds();
            sender.sendMessage("Worlds deleted, now loading backup: " + backupName);
            // Here you would add the logic to load the backup
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("load-backup")) {
            if (args.length == 1) {
                return List.of("normal", "forced");
            } else if (args.length == 2) {
                List<String> backups = new ArrayList<>();
                File backupFolder = args[0].equalsIgnoreCase("forced") ? 
                    new File(FORCED_BACKUP_FOLDER) : new File(BACKUP_FOLDER);
                
                if (backupFolder.exists()) {
                    String prefix = args[0].equalsIgnoreCase("forced") ? "forced_backup_" : "backup_";
                    File[] files = backupFolder.listFiles((dir, name) -> name.startsWith(prefix));
                    if (files != null) {
                        for (File file : files) {
                            backups.add(file.getName());
                        }
                    }
                }
                return backups;
            }
        }
        return null;
    }

    private void deleteCurrentWorlds() {
        File serverPath = getServer().getWorldContainer();
        String[] worlds = {"world", "world_nether", "world_the_end"};
        
        for (String worldName : worlds) {
            try {
                File worldFolder = new File(serverPath, worldName);
                if (worldFolder.exists()) {
                    deleteDirectory(worldFolder.toPath());
                    getLogger().info("Deleted world: " + worldName);
                }
            } catch (IOException e) {
                getLogger().severe("Failed to delete " + worldName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
