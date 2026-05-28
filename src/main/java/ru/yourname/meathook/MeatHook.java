package ru.yourname.meathook;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chain;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MeatHook extends JavaPlugin implements Listener {

    private NamespacedKey hookKey;
    private NamespacedKey meatKey;
    private NamespacedKey holderKey;

    private final Map<UUID, Location> hookedPlayers = new HashMap<>();
    private final Map<UUID, UUID> playerToAnvil = new HashMap<>();

    @Override
    public void onEnable() {
        this.hookKey = new NamespacedKey(this, "meat_hook_block");
        this.meatKey = new NamespacedKey(this, "hook_meat_type");
        this.holderKey = new NamespacedKey(this, "hook_holder_uuid");
        
        getServer().getPluginManager().registerEvents(this, this);
        
        if (getCommand("getmeathook") != null) {
            getCommand("getmeathook").setExecutor(this::onCommand);
        }

        registerMeatHookRecipe();

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Map.Entry<UUID, Location> entry : hookedPlayers.entrySet()) {
                Player p = getServer().getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    p.teleport(entry.getValue());
                }
            }
        }, 1L, 1L);
    }

    private void registerMeatHookRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "meat_hook_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, getHookItem());
        recipe.shape("C", "C", "N");
        recipe.setIngredient('C', Material.CHAIN);
        recipe.setIngredient('N', Material.IRON_NUGGET);
        
        if (getServer().getRecipe(recipeKey) != null) {
            getServer().removeRecipe(recipeKey);
        }
        getServer().addRecipe(recipe);
    }

    public boolean onCommand(CommandSender sender, org.bukkit.command.Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        player.getInventory().addItem(getHookItem());
        player.sendMessage(ChatColor.GREEN + "Вы получили Мясной крюк!");
        return true;
    }

    // 1. СБОРКА ВЫСОКОДЕТАЛИЗИРОВАННОГО КОВАННОГО КРЮКА (8 элементов трансформации)
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {
            return;
        }

        Block block = event.getBlockPlaced();
        if (block.getBlockData() instanceof Chain) {
            Chain chain = (Chain) block.getBlockData();
            if (chain.getAxis() != org.bukkit.Axis.Y) return;
        }

        Location baseLoc = block.getLocation();

        // Главный узел (Муфта крепления). Сплющенная наковальня, обнимающая основание цепи.
        ItemDisplay mainAnvil = createHookPart(baseLoc, Material.ANVIL, 
            new Vector3f(0.00f, -0.48f, 0.00f), new Vector3f(0.24f, 0.12f, 0.24f), 0);
        mainAnvil.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);

        // Вертикальное основание багра (Толстый кованый квадратный прут)
        createHookPart(baseLoc, Material.ANVIL, new Vector3f(0.00f, -0.62f, 0.00f), new Vector3f(0.09f, 0.22f, 0.09f), 0);

        // Плавный кованый изгиб J-формы из 5 сегментов с шагом вращения по оси X
        createHookPart(baseLoc, Material.ANVIL, new Vector3f(0.00f, -0.74f, 0.02f), new Vector3f(0.08f, 0.14f, 0.08f), 25);
        createHookPart(baseLoc, Material.ANVIL, new Vector3f(0.00f, -0.82f, 0.08f), new Vector3f(0.08f, 0.14f, 0.08f), 55);
        createHookPart(baseLoc, Material.ANVIL, new Vector3f(0.00f, -0.85f, 0.18f), new Vector3f(0.08f, 0.14f, 0.08f), 85);
        createHookPart(baseLoc, Material.ANVIL, new Vector3f(0.00f, -0.81f, 0.28f), new Vector3f(0.07f, 0.14f, 0.07f), 115);
        createHookPart(baseLoc, Material.ANVIL, new Vector3f(0.00f, -0.73f, 0.35f), new Vector3f(0.06f, 0.14f, 0.06f), 140);

        // Остриё крюка (Финальное стальное жало, пробивающее туши)
        createHookPart(baseLoc, Material.IRON_NUGGET, new Vector3f(0.00f, -0.64f, 0.38f), new Vector3f(0.55f, 0.55f, 0.55f), 165);
    }

    // Умный хелпер: спавнит сущность строго в центре, двигая лишь визуальный меш
    private ItemDisplay createHookPart(Location blockLoc, Material mat, Vector3f translation, Vector3f scale, float angleDeg) {
        Location spawnLoc = blockLoc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = (ItemDisplay) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(mat));
        
        Transformation t = display.getTransformation();
        t.getTranslation().set(translation);
        t.getScale().set(scale);
        if (angleDeg != 0) {
            t.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(angleDeg), 1, 0, 0));
        }
        display.setTransformation(t);
        display.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);
        return display;
    }

    // 2. ВЗАИМОДЕЙСТВИЕ
    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHAIN) return;

        ItemDisplay anvil = findAnvilDisplay(block);
        if (anvil == null) return; 

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        
        String currentState = anvil.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);

        // КРЮК СВОБОДЕН
        if (currentState == null) {
            // Вешаем абсолютно ЛЮБОЕ мясо или рыбу
            if (handItem.getType() != Material.AIR && isMeat(handItem.getType())) {
                Material meatType = handItem.getType();
                handItem.setAmount(handItem.getAmount() - 1);

                // Спавним кусок мяса, насаженный прямо на новое кованое остриё
                ItemDisplay meatDisplay = createHookPart(block.getLocation(), meatType, 
                    new Vector3f(0.00f, -0.62f, 0.38f), new Vector3f(0.7f, 0.7f, 0.7f), 0);
                meatDisplay.addScoreboardTag("meathook_meat_render");

                anvil.getPersistentDataContainer().set(meatKey, PersistentDataType.STRING, meatType.toString());
                player.sendMessage(ChatColor.YELLOW + "Вы насадили мясо на остриё кованого крюка.");
            } 
            // Вешаем ИГРОКА
            else if (handItem.getType() == Material.AIR) {
                // Игрок фиксируется грудью прямо на уровне острия крюка
                Location hangLoc = block.getLocation().add(0.5, -1.55, 0.88);
                hangLoc.setYaw(player.getLocation().getYaw());
                hangLoc.setPitch(15); // Небольшой наклон головы для реализма мучений
                
                player.teleport(hangLoc);
                player.setGravity(false);
                
                hookedPlayers.put(player.getUniqueId(), hangLoc);
                playerToAnvil.put(player.getUniqueId(), anvil.getUniqueId());
                
                anvil.getPersistentDataContainer().set(meatKey, PersistentDataType.STRING, "PLAYER");
                anvil.getPersistentDataContainer().set(holderKey, PersistentDataType.STRING, player.getUniqueId().toString());
                
                player.sendMessage(ChatColor.RED + "Вы зацепились за мясной багор! Нажмите SHIFT, чтобы слезть.");
            }
        }
        // СНЯТИЕ МЯСА
        else if (!currentState.equals("PLAYER")) {
            Material meatType = Material.valueOf(currentState);
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(new ItemStack(meatType));
            } else {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(meatType));
            }

            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            for (Entity e : center.getWorld().getNearbyEntities(center, 0.2, 0.2, 0.2)) {
                if (e instanceof ItemDisplay && e.getScoreboardTags().contains("meathook_meat_render")) {
                    e.remove();
                }
            }

            anvil.getPersistentDataContainer().remove(meatKey);
            player.sendMessage(ChatColor.GOLD + "Вы сняли тушу с крюка.");
        } else {
            player.sendMessage(ChatColor.RED + "На этом багре уже висит человек!");
        }
    }

    // Слезть с крюка
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        
        if (hookedPlayers.containsKey(player.getUniqueId())) {
            hookedPlayers.remove(player.getUniqueId());
            player.setGravity(true);
            
            UUID anvilUid = playerToAnvil.remove(player.getUniqueId());
            if (anvilUid != null) {
                Entity anvil = getServer().getEntity(anvilUid);
                if (anvil instanceof ItemDisplay) {
                    anvil.getPersistentDataContainer().remove(meatKey);
                    anvil.getPersistentDataContainer().remove(holderKey);
                }
            }
            player.sendMessage(ChatColor.GOLD + "Вы аккуратно снялись с крюка.");
        }
    }

    // 3. УНИЧТОЖЕНИЕ БЛОКА И ОЧИСТКА
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHAIN) return;

        ItemDisplay anvil = findAnvilDisplay(block);
        if (anvil == null) return;

        String currentState = anvil.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);
        if (currentState != null) {
            if (currentState.equals("PLAYER")) {
                String playerUuidStr = anvil.getPersistentDataContainer().get(holderKey, PersistentDataType.STRING);
                if (playerUuidStr != null) {
                    UUID pUuid = UUID.fromString(playerUuidStr);
                    hookedPlayers.remove(pUuid);
                    playerToAnvil.remove(pUuid);
                    Player p = getServer().getPlayer(pUuid);
                    if (p != null) {
                        p.setGravity(true);
                        p.sendMessage(ChatColor.GOLD + "Крюк вырван из потолка, вы упали!");
                    }
                }
            } else {
                Material meatType = Material.valueOf(currentState);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(meatType));
            }
        }

        // Удаляем всю кастомную модель (все сущности в этой точке)
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : center.getWorld().getNearbyEntities(center, 0.3, 0.3, 0.3)) {
            if (e instanceof ItemDisplay && e.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {
                e.remove();
            }
        }

        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), getHookItem());
        event.setDropItems(false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanPlayerHook(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanPlayerHook(event.getEntity());
    }

    private void cleanPlayerHook(Player player) {
        UUID pUuid = player.getUniqueId();
        if (hookedPlayers.containsKey(pUuid)) {
            hookedPlayers.remove(pUuid);
            player.setGravity(true);
            UUID anvilUid = playerToAnvil.remove(pUuid);
            if (anvilUid != null) {
                Entity anvil = getServer().getEntity(anvilUid);
                if (anvil instanceof ItemDisplay) {
                    anvil.getPersistentDataContainer().remove(meatKey);
                    anvil.getPersistentDataContainer().remove(holderKey);
                }
            }
        }
    }

    private ItemDisplay findAnvilDisplay(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : center.getWorld().getNearbyEntities(center, 0.1, 0.1, 0.1)) {
            if (e instanceof ItemDisplay) {
                ItemDisplay id = (ItemDisplay) e;
                if (id.getItemStack() != null && id.getItemStack().getType() == Material.ANVIL) {
                    if (id.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {
                        return id;
                    }
                }
            }
        }
        return null;
    }

    private boolean isMeat(Material material) {
        String name = material.name();
        return name.contains("BEEF") || name.contains("PORK") || name.contains("CHICKEN") 
            || name.contains("MUTTON") || name.contains("RABBIT") || name.contains("FLESH") 
            || name.contains("COD") || name.contains("SALMON") || name.contains("ROTTEN");
    }

    private ItemStack getHookItem() {
        ItemStack hook = new ItemStack(Material.CHAIN);
        ItemMeta meta = hook.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Мясной крюк");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Тяжелая кованая сталь");
            lore.add(ChatColor.GRAY + "Повесьте на потолок.");
            lore.add(ChatColor.GRAY + "Клик мясом - насадить тушу.");
            lore.add(ChatColor.GRAY + "Клик пустой рукой - повесить себя.");
            meta.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);
            hook.setItemMeta(meta);
        }
        return hook;
    }
}
