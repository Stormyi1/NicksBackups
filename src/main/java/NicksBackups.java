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
import java.sql.Timestamp;

public class NicksBackups extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final String BACKUP_FOLDER = "/backups/normal";
    private static final String FORCED_BACKUP_FOLDER = "/backups/forced";

    @Override
    public void onEnable() {
        this.getCommand("load-backup").setExecutor(this);
        this.getCommand("load-backup").setTabCompleter(this);
        this.getCommand("force-backup").setExecutor(this);
        
        // Debug logging
        getLogger().info("Normal backup path exists: " + new File(BACKUP_FOLDER).exists());
        getLogger().info("Forced backup path exists: " + new File(FORCED_BACKUP_FOLDER).exists());
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

            try {
                deleteCurrentWorlds();
                sender.sendMessage("Worlds deleted, now loading backup: " + backupName);
                copyBackupToServer(backupDir);
                sender.sendMessage("Backup restored successfully. Restarting server...");
                restartServer();
                return true;
            } catch (IOException e) {
                sender.sendMessage("Error while restoring backup: " + e.getMessage());
                getLogger().severe("Backup restoration failed: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else if (command.getName().equalsIgnoreCase("force-backup")) {
            // Create timestamp for backup name
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            String backupName = "forced_backup_" + timestamp.getTime() + "_uc";
            
            File forcedBackupDir = new File(FORCED_BACKUP_FOLDER, backupName);
            if (!forcedBackupDir.exists()) {
                forcedBackupDir.mkdirs();
            }

            try {
                // Copy current worlds to backup
                File serverDir = getServer().getWorldContainer();
                String[] worlds = {"world", "world_nether", "world_the_end"};
                
                for (String worldName : worlds) {
                    File sourceWorld = new File(serverDir, worldName);
                    File destWorld = new File(forcedBackupDir, worldName);
                    if (sourceWorld.exists()) {
                        copyDirectory(sourceWorld.toPath(), destWorld.toPath());
                        sender.sendMessage("Backed up world: " + worldName);
                    }
                }
                
                sender.sendMessage("Forced backup created successfully: " + backupName);
                return true;
                
            } catch (IOException e) {
                sender.sendMessage("Error creating forced backup: " + e.getMessage());
                getLogger().severe("Forced backup failed: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
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

    private void deleteCurrentWorlds() throws IOException {
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

    private void copyBackupToServer(File backupDir) throws IOException {
        File serverDir = getServer().getWorldContainer();
        String[] worlds = {"world", "world_nether", "world_the_end"};
        for (String worldName : worlds) {
            File sourceWorld = new File(backupDir, worldName);
            File destWorld = new File(serverDir, worldName);
            if (sourceWorld.exists()) {
                copyDirectory(sourceWorld.toPath(), destWorld.toPath());
                getLogger().info("Restored " + worldName + " from backup");
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void restartServer() {
        getServer().getScheduler().runTaskLater(this, () -> {
            getServer().broadcastMessage("Server is restarting to apply backup...");
            getServer().spigot().restart();
        }, 60L); // 3 second delay
    }
}
