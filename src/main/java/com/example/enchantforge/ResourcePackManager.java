package com.example.enchantforge;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import java.util.zip.*;

public class ResourcePackManager {

    private final Plugin plugin;

    private String configuredUrl;
    private String effectiveUrl;
    private boolean required;
    private String packHashHex;
    private String httpHost;
    private int httpPort;
    private HttpServer httpServer;

    public ResourcePackManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void generatePack() {
        File packFile = new File(plugin.getDataFolder(), "enchantforge_pack.zip");
        try {
            buildZip(packFile);
            packHashHex = sha1Hex(packFile);
            plugin.getLogger().info("Resource pack generated.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to generate resource pack: " + e.getMessage());
            return;
        }
        startHttpServer();
    }

    public void reloadSettings() {
        loadConfig();
        startHttpServer();
    }

    public void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
    }

    public void sendPackTo(Player player) {
        if (effectiveUrl == null || effectiveUrl.isEmpty() || packHashHex == null) return;
        player.setResourcePack(effectiveUrl, packHashHex, required,
                Component.text("EnchantForge: custom heart colors"));
    }

    // -------------------------------------------------------------------------

    private void loadConfig() {
        configuredUrl = plugin.getConfig().getString("resource-pack.url", "");
        required      = plugin.getConfig().getBoolean("resource-pack.required", false);
        httpHost      = plugin.getConfig().getString("resource-pack.host", "");
        httpPort      = plugin.getConfig().getInt("resource-pack.port", 8080);
    }

    private void startHttpServer() {
        stopHttpServer();

        if (!httpHost.isEmpty()) {
            try {
                File packFile = new File(plugin.getDataFolder(), "enchantforge_pack.zip");
                httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
                httpServer.createContext("/enchantforge_pack.zip", exchange -> {
                    try {
                        if (!packFile.exists()) {
                            exchange.sendResponseHeaders(404, -1);
                            exchange.close();
                            return;
                        }
                        byte[] data = Files.readAllBytes(packFile.toPath());
                        exchange.getResponseHeaders().set("Content-Type", "application/zip");
                        exchange.sendResponseHeaders(200, data.length);
                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(data);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("Error serving resource pack: " + e.getMessage());
                    }
                });
                httpServer.setExecutor(Executors.newSingleThreadExecutor(
                        r -> new Thread(r, "EnchantForge-PackServer")));
                httpServer.start();
                plugin.getLogger().info("Resource pack server started on port " + httpPort + ".");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to start resource pack server on port "
                        + httpPort + ": " + e.getMessage());
            }
        }

        // Resolve effective URL: explicit config takes priority, then auto-compute from host+port
        if (!configuredUrl.isEmpty()) {
            effectiveUrl = configuredUrl;
        } else if (!httpHost.isEmpty()) {
            effectiveUrl = "http://" + httpHost + ":" + httpPort + "/enchantforge_pack.zip";
            plugin.getLogger().info("Resource pack URL: " + effectiveUrl);
        } else {
            effectiveUrl = "";
            plugin.getLogger().warning(
                "Resource pack host not configured -- absorption hearts will use default color. " +
                "Set resource-pack.host in config.yml.");
        }
    }

    // -------------------------------------------------------------------------

    private static void buildZip(File dest) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dest))) {
            addText(zos, "pack.mcmeta", packMeta());
            // Absorption heart recolors
            String base = "assets/minecraft/textures/gui/sprites/hud/heart/";
            addBytes(zos, base + "absorbing_full.png",          heartPng(true,  false));
            addBytes(zos, base + "absorbing_half.png",          heartPng(false, false));
            addBytes(zos, base + "absorbing_full_blinking.png", heartPng(true,  true));
            addBytes(zos, base + "absorbing_half_blinking.png", heartPng(false, true));
        }
    }

    private static String packMeta() {
        return "{\n  \"pack\": {\n    \"pack_format\": 42," +
               "\n    \"description\": \"EnchantForge custom heart colors\"\n  }\n}\n";
    }

    private static void addText(ZipOutputStream zos, String name, String text) throws IOException {
        addBytes(zos, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void addBytes(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] heartPng(boolean full, boolean blinking) throws Exception {
        final int T = 0x00000000;
        final int D = 0xFF004545;
        final int F = blinking ? 0xFF40DCC8 : 0xFF00B8A0;
        final int H = 0xFF80FFE8;

        final int[][] fullMap = {
            {T, T, T, T, T, T, T, T, T},
            {T, D, F, D, T, D, F, D, T},
            {D, F, H, F, D, F, H, F, D},
            {D, F, F, F, F, F, F, F, D},
            {D, F, F, F, F, F, F, F, D},
            {T, D, F, F, F, F, F, D, T},
            {T, T, D, F, F, F, D, T, T},
            {T, T, T, D, F, D, T, T, T},
            {T, T, T, T, D, T, T, T, T},
        };

        final int[][] halfMap = {
            {T, T, T, T, T, T, T, T, T},
            {T, D, F, D, T, T, T, T, T},
            {D, F, H, F, D, T, T, T, T},
            {D, F, F, F, F, T, T, T, T},
            {D, F, F, F, F, T, T, T, T},
            {T, D, F, F, F, T, T, T, T},
            {T, T, D, F, F, T, T, T, T},
            {T, T, T, D, F, T, T, T, T},
            {T, T, T, T, D, T, T, T, T},
        };

        int[][] pixels = full ? fullMap : halfMap;
        BufferedImage img = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++)
            for (int x = 0; x < 9; x++)
                img.setRGB(x, y, pixels[y][x]);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private static String sha1Hex(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
