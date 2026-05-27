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

        Location loc = block.getLocation().add(0.5, -0.6, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.getPersistentDataContainer().set(hookKey, PersistentDataType.BOOLEAN, true);

        Location part1Loc = block.getLocation().add(0.5, -0.1, 0.5);
        ItemDisplay part1 = (ItemDisplay) part1Loc.getWorld().spawnEntity(part1Loc, EntityType.ITEM_DISPLAY);
        part1.setItemStack(new ItemStack(Material.CHAIN));
        Transformation t1 = part1.getTransformation();
        t1.getScale().set(0.5f, 0.5f, 0.5f);
        part1.setTransformation(t1);
        stand.addScoreboardTag("part_" + part1.getUniqueId().toString());

        Location part2Loc = block.getLocation().add(0.5, -0.32, 0.58);
        ItemDisplay part2 = (ItemDisplay) part2Loc.getWorld().spawnEntity(part2Loc, EntityType.ITEM_DISPLAY);
        part2.setItemStack(new ItemStack(Material.CHAIN));
        Transformation t2 = part2.getTransformation();
        t2.getScale().set(0.5f, 0.3f, 0.5f);
        t2.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(45), 1, 0, 0));
        part2.setTransformation(t2);
        stand.addScoreboardTag("part_" + part2.getUniqueId().toString());
    }

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

            Location meatLoc = stand.getLocation().add(0, -0.15, 0.12);
            ItemDisplay meatDisplay = (ItemDisplay) meatLoc.getWorld().spawnEntity(meatLoc, EntityType.ITEM_DISPLAY);
            meatDisplay.setItemStack(new ItemStack(meatType));

            Transformation tMeat = meatDisplay.getTransformation();
            tMeat.getScale().set(0.55f, 0.55f, 0.55f);
            meatDisplay.setTransformation(tMeat);

            stand.getPersistentDataContainer().set(meatKey, PersistentDataType.STRING, meatType.toString());
            stand.addScoreboardTag("meat_" + meatDisplay.getUniqueId().toString());

            player.sendMessage(ChatColor.YELLOW + "Вы повесили мясо на цепной крюк.");

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

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHAIN) return;

        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, -0.5, 0.5), 1.2, 1.2, 1.2)) {
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
