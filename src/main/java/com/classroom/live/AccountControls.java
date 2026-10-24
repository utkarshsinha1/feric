package com.classroom.live;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

final class AccountControls extends JPanel {
    private final String email;
    private final String role;
    private final Window owner;

    AccountControls(Window owner, SupabaseClient client, String role) {
        super(new BorderLayout());
        this.owner = owner;
        this.role = role;
        email = client.signedInEmail();
        setOpaque(true);
        setBackground(IdeTheme.SIDEBAR);
        setPreferredSize(new Dimension(48, 100));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, IdeTheme.BORDER));

        JPanel bottomActions = new JPanel(new GridLayout(2, 1, 0, 6));
        bottomActions.setOpaque(false);
        bottomActions.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JButton account = new BrandMarkButton();
        account.setToolTipText(email.isBlank() ? "Account details unavailable" : email);
        account.getAccessibleContext().setAccessibleName("Feric account");
        account.addActionListener(event -> showSettings());
        bottomActions.add(account);

        JButton settings = new GearButton();
        settings.setToolTipText("Settings");
        settings.getAccessibleContext().setAccessibleName("Settings");
        settings.addActionListener(event -> showSettings());
        bottomActions.add(settings);
        add(bottomActions, BorderLayout.SOUTH);
    }

    private void showSettings() {
        JDialog dialog = new JDialog(owner, "Feric Settings", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        IdeTheme.styleDialog(dialog);
        dialog.setLayout(new BorderLayout(0, 16));

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(IdeTheme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));

        JLabel title = new JLabel("Settings");
        title.setForeground(IdeTheme.TEXT);
        title.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Manage your account and workspace preferences.");
        subtitle.setForeground(IdeTheme.MUTED);
        subtitle.setFont(IdeTheme.UI_FONT.deriveFont(12f));
        JPanel heading = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(subtitle);

        JPanel details = new JPanel(new java.awt.GridLayout(0, 1, 0, 10));
        details.setBackground(IdeTheme.PANEL);
        details.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(IdeTheme.BORDER),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        JLabel accountHeading = sectionLabel("ACCOUNT");
        JLabel accountValue = new JLabel(email.isBlank()
            ? "Account identity unavailable" : email);
        accountValue.setForeground(IdeTheme.TEXT);
        accountValue.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 13f));
        JLabel roleValue = new JLabel(role);
        roleValue.setForeground(IdeTheme.MUTED);
        roleValue.setFont(IdeTheme.UI_FONT.deriveFont(11f));
        details.add(accountHeading);
        details.add(accountValue);
        details.add(roleValue);

        JLabel appearanceHeading = sectionLabel("APPEARANCE");
        JLabel appearance = new JLabel("Dark theme");
        appearance.setForeground(IdeTheme.TEXT);
        appearance.setFont(IdeTheme.UI_FONT.deriveFont(12f));
        JPanel appearancePanel = new JPanel(new BorderLayout(0, 7));
        appearancePanel.setOpaque(false);
        appearancePanel.add(appearanceHeading, BorderLayout.NORTH);
        appearancePanel.add(appearance, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout(0, 18));
        body.setOpaque(false);
        body.add(details, BorderLayout.NORTH);
        body.add(appearancePanel, BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);

        JButton copyEmail = new JButton("Copy email");
        IdeTheme.styleButton(copyEmail);
        copyEmail.setEnabled(!email.isBlank());
        copyEmail.addActionListener(event -> {
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(email), null);
                JOptionPane.showMessageDialog(
                    dialog, "Account email copied.", "Feric Settings",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IllegalStateException exception) {
                JOptionPane.showMessageDialog(
                    dialog, "Clipboard is busy. Please try again.",
                    "Feric Settings", JOptionPane.WARNING_MESSAGE);
            }
        });
        JButton close = new JButton("Done");
        IdeTheme.stylePrimaryButton(close);
        close.addActionListener(event -> dialog.dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 20, 16, 20));
        actions.add(copyEmail);
        actions.add(close);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(close);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(430, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(IdeTheme.MUTED);
        label.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 10f));
        return label;
    }

    private abstract static class RailButton extends JButton {
        private RailButton(String tooltip) {
            setPreferredSize(new Dimension(48, 42));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setBorder(null);
            setToolTipText(tooltip);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintIcon(g);
            g.dispose();
        }

        protected abstract void paintIcon(Graphics2D graphics);

        protected Color iconColor() {
            return getModel().isRollover() ? IdeTheme.TEXT : IdeTheme.MUTED;
        }
    }

    private static final class BrandMarkButton extends RailButton {
        private BrandMarkButton() {
            super("Account settings");
        }

        @Override
        protected void paintIcon(Graphics2D g) {
            int diameter = 30;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            g.setPaint(new java.awt.GradientPaint(
                x, y + diameter, new Color(119, 76, 255),
                x + diameter, y, new Color(46, 177, 255)));
            g.fillOval(x, y, diameter, diameter);

            g.setColor(new Color(238, 244, 255));
            g.fillOval(x + 10, y + 5, 10, 10);
            g.fillRoundRect(x + 5, y + 17, 20, 11, 10, 10);

            if (getModel().isRollover() || getModel().isPressed()) {
                g.setColor(new Color(255, 255, 255, 80));
                g.drawOval(x, y, diameter - 1, diameter - 1);
            }
        }
    }

    private static final class GearButton extends RailButton {
        private GearButton() {
            super("Settings");
        }

        @Override
        protected void paintIcon(Graphics2D g) {
            g.setColor(iconColor());
            g.setStroke(new java.awt.BasicStroke(2.4f, java.awt.BasicStroke.CAP_ROUND,
                java.awt.BasicStroke.JOIN_ROUND));
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            for (int tooth = 0; tooth < 8; tooth++) {
                double angle = Math.PI * tooth / 4;
                int innerX = centerX + (int) Math.round(Math.cos(angle) * 9);
                int innerY = centerY + (int) Math.round(Math.sin(angle) * 9);
                int outerX = centerX + (int) Math.round(Math.cos(angle) * 14);
                int outerY = centerY + (int) Math.round(Math.sin(angle) * 14);
                g.drawLine(innerX, innerY, outerX, outerY);
            }
            g.setStroke(new java.awt.BasicStroke(2.4f));
            g.drawOval(centerX - 9, centerY - 9, 18, 18);
            g.setColor(IdeTheme.SIDEBAR);
            g.fillOval(centerX - 3, centerY - 3, 6, 6);
        }
    }
}
