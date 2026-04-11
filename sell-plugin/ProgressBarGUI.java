import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProgressBarGUI extends JFrame {
    private JProgressBar progressBar;
    private JButton startButton;
    private Timer timer;
    private int progress = 0;

    public ProgressBarGUI() {
        setTitle("Selling Progress");
        setSize(400, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        add(progressBar);

        startButton = new JButton("Start Selling");
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startProgress();
            }
        });
        add(startButton);

        setVisible(true);
    }

    private void startProgress() {
        progress = 0;
        progressBar.setValue(progress);

        timer = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (progress < 100) {
                    progress++;
                    progressBar.setValue(progress);
                } else {
                    timer.stop();
                }
            }
        });
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ProgressBarGUI());
    }
}