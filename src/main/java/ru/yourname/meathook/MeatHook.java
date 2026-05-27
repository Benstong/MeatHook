package ru.yourname.meathook;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chain;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class MeatHook extends JavaPlugin implements Listener {

    private NamespacedKey hookKey;
    private NamespacedKey meatKey;

    @Override
    public void onEnable() {
        this.hookKey = new NamespacedKey(this, "meat_hook_block");
        this.meatKey = new NamespacedKey(this, "hook_meat_type");
        getServer().getPluginManager().registerEvents(this, this);
        
        if (getCommand("getmeathook") != null) {
            getCommand("getmeathook").setExecutor(this::onCommand);
        }

        registerMeatHookRecipe();
    }

    private void registerMeatHookRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "meat_hook_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, getHookItem());
        
        recipe.shape(
            "C",
            "C",
            "N"
        );
        
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

    // 1. УСТАНОВКА (Сборка сложной 3D-модели из 6 элементов)
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

        // Невидимый базовый хитбокс
        Location loc = block.getLocation().add(0.5, -0.6, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);

        // ЭЛЕМЕНТ 1: Тяжелое кованое основание крюка (Мини-наковальня сверху)
        createHookPart(stand, block.getLocation().add(0.5, -0.05, 0.5), Material.ANVIL, 
            new Vector3f(0.18f, 0.15f, 0.18f), 0, 0, 0);

        // ЭЛЕМЕНТ 2: Вертикальный железный стержень (Толстая центральная цепь)
        createHookPart(stand, block.getLocation().add(0.5, -0.22, 0.5), Material.CHAIN, 
            new Vector3f(0.6f, 0.6f, 0.6f), 0, 0, 0);

        // ЭЛЕМЕНТ 3: Начало изгиба (Наклон вниз-вперед)
        createHookPart(stand, block.getLocation().add(0.5, -0.42, 0.53), Material.CHAIN, 
            new Vector3f(0.5f, 0.4f, 0.5f), 30, 1, 0, 0);

        // ЭЛЕМЕНТ 4: Дно изгиба крюка (Сильный наклон вперед)
        createHookPart(stand, block.getLocation().add(0.5, -0.52, 0.62), Material.CHAIN, 
            new Vector3f(0.5f, 0.4f, 0.5f), 75, 1, 0, 0);

        // ЭЛЕМЕНТ 5: Подъем изгиба (Наклон вверх)
        createHookPart(stand, block.getLocation().add(0.5, -0.46, 0.73), Material.CHAIN, 
            new Vector3f(0.5f, 0.4f, 0.5f), 120, 1, 0, 0);

        // ЭЛЕМЕНТ 6: Острое жало крюка (Железный самородок на конце)
        createHookPart(stand, block.getLocation().add(0.5, -0.34, 0.76), Material.IRON_NUGGET, 
            new Vector3f(0.6f, 0.6f, 0.6f), 145, 1, 0, 0);
    }

    // Вспомогательный метод для быстрой генерации частей модели
    private void createHookPart(ArmorStand stand, Location loc, Material mat, Vector3f scale, float angleDeg, float x, float y, float z) {
        ItemDisplay display = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(mat));
        Transformation t = display.getTransformation();
        t.getScale().set(scale);
        if (angleDeg != 0) {
            t.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(angleDeg), x, y, z));
        }
        display.setTransformation(t);
        stand.addScoreboardTag("part_" + display.getUniqueId().toString());
    }

    // 2. ВЗАИМОДЕЙСТВИЕ (Вешаем мясо ровно на остриё нового массивного крюка)
    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;

        ArmorStand stand = (ArmorStand) entity;
        if (!stand.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItem(event.getHand());
        String currentMeat = stand.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);

        if (currentMeat == null && isRawMeat(handItem.getType())) {
            Material meatType = handItem.getType();
            handItem.setAmount(handItem.getAmount() - 1);

            // Мясо теперь спавнится ровно на кончике нашего нового длинного жала (Z смещен вперед)
            Location meatLoc = stand.getLocation().add(0, 0.12, 0.26);
            ItemDisplay meatDisplay = (ItemDisplay) meatLoc.getWorld().spawnEntity(meatLoc, EntityType.ITEM_DISPLAY);
            meatDisplay.setItemStack(new ItemStack(meatType));

            Transformation tMeat = meatDisplay.getTransformation();
            tMeat.getScale().set(0.6f, 0.6f, 0.6f); // Жирный, хороший кусок мяса
            meatDisplay.setTransformation(tMeat);

            stand.getPersistentDataContainer().set(meatKey, PersistentDataType.STRING, meatType.toString());
            stand.addScoreboardTag("meat_" + meatDisplay.getUniqueId().toString());

            player.sendMessage(ChatColor.YELLOW + "Вы повесили мясо на кованый крюк.");

        } else if (currentMeat != null) {
            Material meatType = Material.valueOf(currentMeat);

            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(new ItemStack(meatType));
            } else {
                stand.getLocation().getWorld().dropItemNaturally(stand.getLocation().add(0, 0.5, 0), new ItemStack(meatType));
            }

            removeEntitiesByTag(stand, "meat_");
            stand.getPersistentDataContainer().remove(meatKey);
            stand.getScoreboardTags().removeIf(tag -> tag.startsWith("meat_"));
            player.sendMessage(ChatColor.GOLD + "Вы сняли мясо с крюка.");
        }
    }

    // 3. РАЗРУШЕНИЕ БЛОКА
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHAIN) return;

        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, -0.5, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand) entity;
                if (stand.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {

                    String currentMeat = stand.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);
                    if (currentMeat != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.valueOf(currentMeat)));
                        removeEntitiesByTag(stand, "meat_");
                    }

                    removeEntitiesByTag(stand, "part_");
                    block.getWorld().dropItemNaturally(block.getLocation(), getHookItem());
                    stand.remove();
                    event.setDropItems(false);
                    break;
                }
            }
        }
    }

    private void removeEntitiesByTag(ArmorStand stand, String prefix) {
        for (String tag : stand.getScoreboardTags()) {
            if (tag.startsWith(prefix)) {
                String uuidStr = tag.replace(prefix, "");
                for (Entity e : stand.getNearbyEntities(1.5, 1.5, 1.5)) {
                    if (e.getUniqueId().toString().equals(uuidStr)) {
                        e.remove();
                    }
                }
            }
        }
    }

    private ItemStack getHookItem() {
        ItemStack hook = new ItemStack(Material.CHAIN);
        ItemMeta meta = hook.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Мясной крюк");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Тяжелый кованый крюк.");
            lore.add(ChatColor.GRAY + "Повесьте на потолок, чтобы");
            lore.add(ChatColor.GRAY + "развешивать сырое мясо.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);
            hook.setItemMeta(meta);
        }
        return hook;
    }

    private boolean isRawMeat(Material material) {
        return material == Material.BEEF || 
               material == Material.PORKCHOP || 
               material == Material.CHICKEN || 
               material == Material.MUTTON || 
               material == Material.RABBIT;
    }
}
