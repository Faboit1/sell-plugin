package com.sellplugin.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProgressBarGUI {
    
    private JFrame frame;
    private JProgressBar progressBar;
    private JLabel label;
    private int totalItems;
    private int itemsSold;

    public ProgressBarGUI(int totalItems) {
        this.totalItems = totalItems;
        this.itemsSold = 0;
        createAndShowGUI();
    }
    
    private void createAndShowGUI() {
        frame = new JFrame("Selling Progress");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 200);
        frame.setLayout(new BorderLayout());
        
        label = new JLabel("Selling items: 0% completed (0 / " + totalItems + ")");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        frame.add(label, BorderLayout.NORTH);
        
        progressBar = new JProgressBar(0, totalItems);
        progressBar.setStringPainted(true);
        frame.add(progressBar, BorderLayout.CENTER);
        
        JButton sellButton = new JButton("Sell Next Item");
        sellButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (itemsSold < totalItems) {
                    itemsSold++;
                    updateProgressBar();
                } else {
                    JOptionPane.showMessageDialog(frame, "All items sold!");
                }
            }
        });
        frame.add(sellButton, BorderLayout.SOUTH);
        
        frame.setVisible(true);
    }
    
    private void updateProgressBar() {
        progressBar.setValue(itemsSold);
        int percentage = (int) ((itemsSold / (float) totalItems) * 100);
        label.setText(String.format("Selling items: %d%% completed (%d / %d)", percentage, itemsSold, totalItems));
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ProgressBarGUI(10)); // Example: selling 10 items
    }
}