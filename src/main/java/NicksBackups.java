package dev.nicks;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class NicksBackups extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String SERVER_PATH = getServer().getWorldContainer().getAbsolutePath() + "/";
    private static final String BACKUP_FOLDER = "/home/mcserver/mc_server/backupsPlugin/normal";
    private static final String FORCED_BACKUP_FOLDER = "/home/mcserver/mc_server/backupsPlugin/forced";
    private static final long BACKUP_INTERVAL = 10 * 60 * 20L; // alle 10 Minuten (in Ticks)

    private boolean isLocked = false;

    @Override
    public void onEnable() {
        this.getCommand("load-backup").setExecutor(this);
        this.getCommand("force-backup").setExecutor(this);
        this.getCommand("load-backup").setTabCompleter(this);

        startBackupTask();
    }

    private void startBackupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isLocked) {
                    try {
                        createBackup(false);
                    } catch (IOException e) {
                        getLogger().severe("Failed to create backup: " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(this, 0L, BACKUP_INTERVAL);
    }

    private void createBackup(boolean isForced) throws IOException {
        isLocked = true;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File newBackupFolder;

        if (isForced) {
            newBackupFolder = new File(FORCED_BACKUP_FOLDER, "forced_backup_" + timestamp);
        } else {
            newBackupFolder = new File(BACKUP_FOLDER, "backup_" + timestamp);
        }

        getLogger().info("Creating backup folder: " + newBackupFolder.getAbsolutePath());
        if (!newBackupFolder.mkdirs()) {
            getLogger().warning("Failed to create backup directory or it already exists");
        }

        for (String worldName : new String[]{"world", "world_nether", "world_the_end"}) {
            File sourceDir = new File(SERVER_PATH, worldName);
            File targetDir = new File(newBackupFolder, worldName);

            getLogger().info("Checking source directory: " + sourceDir.getAbsolutePath());
            getLogger().info("Source directory exists: " + sourceDir.exists());
            getLogger().info("Source directory is directory: " + sourceDir.isDirectory());

            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                getLogger().warning("Source directory invalid: " + sourceDir.getAbsolutePath());
                continue;
            }

            getLogger().info("Starting backup of " + worldName);
            try {
                Files.createDirectories(targetDir.toPath());
                copyDirectoryImproved(sourceDir.toPath(), targetDir.toPath());
                getLogger().info("Successfully backed up " + worldName);
            } catch (IOException e) {
                getLogger().severe("Failed to copy " + worldName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        getLogger().info((isForced ? "Forced" : "Regular") + " backup completed at " + timestamp);

        // Nur reguläre Backups löschen
        if (!isForced) {
            File[] backups = new File(BACKUP_FOLDER).listFiles((dir, name) -> name.startsWith("backup_"));
            if (backups != null && backups.length > 3) {
                List<File> backupList = new ArrayList<>(List.of(backups));
                backupList.sort(Comparator.comparingLong(File::lastModified));
                for (int i = 0; i < backupList.size() - 3; i++) {
                    deleteDirectory(backupList.get(i).toPath());
                    getLogger().info("Deleted old backup: " + backupList.get(i).getName());
                }
            }
        }
        isLocked = false;
    }

    private void restoreBackup(File backupDir) throws IOException {
        isLocked = true;
        for (File worldDir : backupDir.listFiles()) {
            Path source = worldDir.toPath();
            Path target = new File(SERVER_PATH, worldDir.getName()).toPath();
            deleteDirectory(target);  // Löscht alte Weltdaten
            copyDirectory(source, target);
        }
        isLocked = false;
    }

    private void copyDirectoryImproved(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                try {
                    Files.copy(dir, targetDir, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (FileAlreadyExistsException e) {
                    if (!Files.isDirectory(targetDir))
                        throw e;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                try {
                    Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    getLogger().warning("Failed to copy file: " + file + " - " + e.getMessage());
                    throw e;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("force-backup")) {
            if (!(sender instanceof Player) || !sender.isOp()) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }
            if (isLocked) {
                sender.sendMessage("A backup or restore process is currently running. Please wait.");
                return true;
            }
            sender.sendMessage("Creating a forced backup...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    createBackup(true);
                    sender.sendMessage("Forced backup created successfully.");
                } catch (IOException e) {
                    sender.sendMessage("Failed to create forced backup: " + e.getMessage());
                }
            });
            return true;
        }

        // Für den load-backup-Befehl weiter
        if (!(sender instanceof Player) || !sender.isOp()) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("load-backup")) {
            if (isLocked) {
                sender.sendMessage("A backup or restore process is currently running. Please wait.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage("Please specify a backup timestamp. Use TAB to view available backups.");
                return true;
            }

            String backupTimestamp = args[0];
            File backupDir = new File(BACKUP_FOLDER, "backup_" + backupTimestamp);

            if (!backupDir.exists()) {
                sender.sendMessage("Backup not found: " + backupTimestamp);
                return true;
            }

            sender.sendMessage("Restoring backup from " + backupTimestamp + "...");
            try {
                restoreBackup(backupDir);
                sender.sendMessage("Backup restored successfully. Server will restart in 5 seconds.");
                Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 100L);
            } catch (IOException e) {
                sender.sendMessage("Failed to restore backup: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("load-backup") && args.length == 1) {
            File backupDir = new File(BACKUP_FOLDER);
            File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("backup_"));
            if (backups == null) return List.of();

            List<String> backupTimestamps = new ArrayList<>();
            for (File backup : backups) {
                String timestamp = backup.getName().replace("backup_", "");
                backupTimestamps.add(timestamp);
            }
            return backupTimestamps;
        }
        return List.of();
    }
}
