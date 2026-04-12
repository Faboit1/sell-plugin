// Update needed to fix ChatColor concatenation

public class CategoryGUI {
    // ... other code
    public void someMethod() {
        String colorString = ChatColor.RED.toString() + "This is a test";
        // ... other code
    }
}