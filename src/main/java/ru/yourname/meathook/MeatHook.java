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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;

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
        
        ItemStack hook = new ItemStack(Material.CHAIN);
        ItemMeta meta = hook.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Мясной крюк");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Повесьте на потолок, чтобы");
            lore.add(ChatColor.GRAY + "развешивать сырое мясо.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);
            hook.setItemMeta(meta);
        }
        
        player.getInventory().addItem(hook);
        player.sendMessage(ChatColor.GREEN + "Вы получили Мясной крюк!");
        return true;
    }

    // 1. СТАВИМ КРЮК (Спавним невидимый ArmorStand под цепью)
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {
            return;
        }

        Block block = event.getBlockPlaced();
        // Проверяем, что цепь вертикальная (висит на потолке/другой цепи)
        if (block.getBlockData() instanceof Chain) {
            Chain chain = (Chain) block.getBlockData();
            if (chain.getAxis() != org.bukkit.Axis.Y) return;
        }

        // Спавним стойку прямо под блоком цепи
        Location loc = block.getLocation().add(0.5, -1.35, 0.5); 
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setInvulnerable(true);
        // Помечаем стойку тегом, чтобы плагин знал, что это наш крюк
        stand.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);

        // Используем Железную мотыгу как сам крюк (в ресурспаках её легко заменить на модель крюка)
        // Поворачиваем руку так, чтобы мотыга смотрела вниз и выглядела как крюк
        stand.setRightArmPose(new EulerAngle(Math.toRadians(180), Math.toRadians(0), Math.toRadians(0)));
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_HOE));
    }

    // 2. ВЗАИМОДЕЙСТВИЕ (Вешаем / снимаем мясо)
    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;
        
        ArmorStand stand = (ArmorStand) entity;
        if (!stand.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) return;

        event.setCancelled(true); // Отменяем стандартную экипировку стойки
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItem(event.getHand());

        String currentMeat = stand.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);

        // Если на крюке ничего нет, и в руке мясо — вешаем его
        if (currentMeat == null && isRawMeat(handItem.getType())) {
            Material meatType = handItem.getType();
            
            // Забираем 1 шт у игрока
            handItem.setAmount(handItem.getAmount() - 1);
            
            // Кладём мясо в ЛЕВУЮ руку стойки и настраиваем её позу
            ItemStack meatDisplay = new ItemStack(meatType);
            stand.getEquipment().setItemInOffHand(meatDisplay);
            stand.setLeftArmPose(new EulerAngle(Math.toRadians(140), Math.toRadians(0), Math.toRadians(0)));
            
            // Запоминаем, какое мясо висит
            stand.getPersistentDataContainer().set(meatKey, PersistentDataType.STRING, meatType.toString());
            player.sendMessage(ChatColor.YELLOW + "Вы повесили мясо на крюк.");
            
        } // Если на крюке есть мясо — снимаем его
        else if (currentMeat != null) {
            Material meatType = Material.valueOf(currentMeat);
            
            // Возвращаем мясо игроку (или дропаем на землю, если инвентарь полон)
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(new ItemStack(meatType));
            } else {
                stand.getLocation().getWorld().dropItemNaturally(stand.getLocation().add(0, 1, 0), new ItemStack(meatType));
            }
            
            // Очищаем стойку
            stand.getEquipment().setItemInOffHand(null);
            stand.getPersistentDataContainer().remove(meatKey);
            player.sendMessage(ChatColor.GOLD + "Вы сняли мясо с крюка.");
        }
    }

    // 3. ЛОМАЕМ КРЮК (Убираем стойку при разрушении цепи)
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHAIN) return;

        // Ищем стойку под сломанным блоком в радиусе 1.5 блоков
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, -1, 0.5), 1.5, 1.5, 1.5)) {
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand) entity;
                if (stand.getPersistentDataContainer().has(hookKey, PersistentDataType.BOOLEAN)) {
                    
                    // Если на ней было мясо, дропаем его
                    String currentMeat = stand.getPersistentDataContainer().get(meatKey, PersistentDataType.STRING);
                    if (currentMeat != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.valueOf(currentMeat)));
                    }
                    
                    // Дропаем сам крюк обратно
                    block.getWorld().dropItemNaturally(block.getLocation(), getHookItem());
                    
                    stand.remove();
                    event.setDropItems(false); // Отключаем дефолтный дроп обычной цепи
                    break;
                }
            }
        }
    }

    // Вспомогательный метод создания предмета для дропа
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

    // Проверка, является ли предмет сырым мясом
    private boolean isRawMeat(Material material) {
        return material == Material.BEEF || 
               material == Material.PORKCHOP || 
               material == Material.CHICKEN || 
               material == Material.MUTTON || 
               material == Material.RABBIT;
    }
}
