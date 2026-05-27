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
        getCommand("getmeathook").setExecutor(this::onCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        player.getInventory().addItem(getHookItem());
        player.sendMessage(ChatColor.GREEN + "Вы получили Мясной крюк!");
        return true;
    }

    // 1. СТАВИМ КРЮК (Создаем сам КРЮК и невидимый хитбокс)
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

        // Создаем невидимый ArmorStand для хитбокса (чтобы можно было кликать ПКМ)
        Location loc = block.getLocation().add(0.5, -0.6, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true); 
        stand.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);

        // СПАВНИМ САМ КРЮК (Используем натяжной крюк TRIPWIRE_HOOK в ItemDisplay)
        Location hookLoc = block.getLocation().add(0.5, -0.15, 0.5);
        ItemDisplay hookDisplay = (ItemDisplay) hookLoc.getWorld().spawnEntity(hookLoc, EntityType.ITEM_DISPLAY);
        hookDisplay.setItemStack(new ItemStack(Material.TRIPWIRE_HOOK));
        
        // Магия трансформации: разворачиваем крюк так, чтобы он смотрел вниз, а не на стену
        Transformation hookTrans = hookDisplay.getTransformation();
        hookTrans.getScale().set(1.5f, 1.5f, 1.5f); // Делаем его чуть крупнее и заметнее
        hookTrans.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0)); // Наклоняем вниз
        hookDisplay.setTransformation(hookTrans);

        // Привязываем крюк к хитбоксу, чтобы удалить его при поломке
        stand.addScoreboardTag("hook_model_" + hookDisplay.getUniqueId().toString());
    }

    // 2. КЛИК ПКМ (Вешаем или снимаем мясо на созданный крюк)
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

        // Вешаем мясо
        if (currentMeat == null && isRawMeat(handItem.getType())) {
            Material meatType = handItem.getType();
            handItem.setAmount(handItem.getAmount() - 1);

            // Спавним мясо прямо под нашим созданным крюком
            Location displayLoc = stand.getLocation().add(0, -0.25, 0);
            ItemDisplay meatDisplay = (ItemDisplay) displayLoc.getWorld().spawnEntity(displayLoc, EntityType.ITEM_DISPLAY);
            meatDisplay.setItemStack(new ItemStack(meatType));
            
            // Настраиваем размер и разворот мяса
            Transformation transformation = meatDisplay.getTransformation();
            transformation.getScale().set(0.6f, 0.6f, 0.6f);
            meatDisplay.setTransformation(transformation);
            
            stand.getPersistentDataContainer().set(meatKey, PersistentDataType.STRING, meatType.toString());
            stand.addScoreboardTag("meat_display_" + meatDisplay.getUniqueId().toString());

            player.sendMessage(ChatColor.YELLOW + "Вы повесили мясо на мясной крюк.");
            
        } // Снимаем мясо
        else if (currentMeat != null) {
            Material meatType = Material.valueOf(currentMeat);
            
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(new ItemStack(meatType));
            } else {
                stand.getLocation().getWorld().dropItemNaturally(stand.getLocation().add(0, 0.5, 0), new ItemStack(meatType));
            }

            // Удаляем только модельку мяса
            removeEntityByTag(stand, "meat_display_");

            stand.getPersistentDataContainer().remove(meatKey);
            // Удаляем тег мяса, но оставляем тег самого крюка
            stand.getScoreboardTags().removeIf(tag -> tag.startsWith("meat_display_"));
            player.sendMessage(ChatColor.GOLD + "Вы сняли мясо с крюка.");
        }
    }

    // 3. ЛОМАЕМ БЛОК (Убираем крюк, хитбокс, мясо и дропаем предметы)
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHAIN) return;

        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, -0.5, 0.5), 1.2, 1.2, 1.2)) {
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand) entity;
                if (stand.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {
                    
                    // Если на крюке было мясо — дропаем его
                    String currentMeat = stand.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);
                    if (currentMeat != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.valueOf(currentMeat)));
                        removeEntityByTag(stand, "meat_display_");
                    }
                    
                    // Удаляем саму 3D модельку крюка
                    removeEntityByTag(stand, "hook_model_");
                    
                    // Выдаем назад предмет мясного крюка
                    block.getWorld().dropItemNaturally(block.getLocation(), getHookItem());
                    stand.remove();
                    event.setDropItems(false); // Отменяем дефолтный дроп ванильной цепи
                    break;
                }
            }
        }
    }

    // Вспомогательный метод для безопасного удаления 3D моделей по тегам UUID
    private void removeEntityByTag(ArmorStand stand, String tagPrefix) {
        for (String tag : stand.getScoreboardTags()) {
            if (tag.startsWith(tagPrefix)) {
                String uuidStr = tag.replace(tagPrefix, "");
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
        meta.setDisplayName(ChatColor.RED + "Мясной крюк");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Повесьте на потолок, чтобы");
        lore.add(ChatColor.GRAY + "развешивать сырое мясо.");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);
        hook.setItemMeta(meta);
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
