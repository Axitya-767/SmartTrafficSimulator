package traffic.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Bottom control bar with [Start / Pause], [Automate Random Lights],
 * [Sync All Lights] buttons and a live status label.
 */
public class ControlPanel extends JPanel {

    private final JButton startPauseBtn;
    private final JButton automateLightsBtn;
    private final JButton syncLightsBtn;
    private final JLabel  statusLabel;

    private static final Color BAR_BG       = new Color(26, 29, 36);
    private static final Color BTN_START    = new Color(45, 150, 85);
    private static final Color BTN_PAUSE    = new Color(200, 75, 55);
    private static final Color BTN_AUTOMATE = new Color(55, 100, 175);
    private static final Color BTN_AUTO_ON  = new Color(185, 130, 35);
    private static final Color BTN_SYNC     = new Color(120, 70, 180);

    public ControlPanel() {
        setBackground(BAR_BG);
        setBorder(new EmptyBorder(10, 18, 10, 18));
        setLayout(new FlowLayout(FlowLayout.CENTER, 14, 4));

        startPauseBtn     = styled("▶  Start",           BTN_START);
        automateLightsBtn = styled("🚦  Automate Lights", BTN_AUTOMATE);
        syncLightsBtn     = styled("🔄  Sync All Green",  BTN_SYNC);

        statusLabel = new JLabel("Paused");
        statusLabel.setForeground(new Color(170, 180, 200));
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));

        add(startPauseBtn);
        add(automateLightsBtn);
        add(syncLightsBtn);
        add(Box.createHorizontalStrut(16));
        add(statusLabel);
    }

    private static JButton styled(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.brighter(), 1),
                new EmptyBorder(8, 16, 8, 16)));
        return b;
    }

    // ── Accessors ──────────────────────────────────────────
    public JButton getStartPauseBtn()     { return startPauseBtn; }
    public JButton getAutomateLightsBtn() { return automateLightsBtn; }
    public JButton getSyncLightsBtn()     { return syncLightsBtn; }
    public JLabel  getStatusLabel()       { return statusLabel; }

    // ── State-driven appearance updates ────────────────────
    public void setRunningState(boolean running) {
        if (running) {
            startPauseBtn.setText("⏸  Pause");
            startPauseBtn.setBackground(BTN_PAUSE);
            statusLabel.setText("Running");
            statusLabel.setForeground(new Color(100, 220, 140));
        } else {
            startPauseBtn.setText("▶  Start");
            startPauseBtn.setBackground(BTN_START);
            statusLabel.setText("Paused");
            statusLabel.setForeground(new Color(170, 180, 200));
        }
    }

    public void setAutomatedState(boolean on) {
        if (on) {
            automateLightsBtn.setText("🚦  Lights: AUTO");
            automateLightsBtn.setBackground(BTN_AUTO_ON);
        } else {
            automateLightsBtn.setText("🚦  Automate Lights");
            automateLightsBtn.setBackground(BTN_AUTOMATE);
        }
    }
}
