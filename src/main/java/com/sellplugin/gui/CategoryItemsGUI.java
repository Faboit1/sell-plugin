import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import java.util.List;
import java.util.ArrayList;

public class CategoryItemsGUI implements Listener {
    private static final int ITEMS_PER_PAGE = 27;
    private List<ItemStack> items;
    private int currentPage;
    private Inventory inventory;

    public CategoryItemsGUI(List<ItemStack> items) {
        this.items = items;
        this.currentPage = 0;
        this.inventory = Bukkit.createInventory(null, 54, "Category Items");
        updateInventory();
    }

    private void updateInventory() {
        inventory.clear();
        int start = currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, items.get(i));
        }
        addNavigationItems();
    }

    private void addNavigationItems() {
        if (currentPage > 0) {
            inventory.setItem(45, createNavigationItem(Material.ARROW, "Previous Page"));
        }
        if ((currentPage + 1) * ITEMS_PER_PAGE < items.size()) {
            inventory.setItem(53, createNavigationItem(Material.ARROW, "Next Page"));
        }
    }

    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        // You can set item meta here down the road if needed
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Category Items")) return;
        event.setCancelled(true);

        if (event.getSlot() == 45 && currentPage > 0) { // Previous Page
            currentPage--;
            updateInventory();
            open((Player) event.getWhoClicked());
        } else if (event.getSlot() == 53 && (currentPage + 1) * ITEMS_PER_PAGE < items.size()) { // Next Page
            currentPage++;
            updateInventory();
            open((Player) event.getWhoClicked());
        }
    }
}