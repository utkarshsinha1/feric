package com.classroom.live;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

public final class PasswordResetDialog {
    private PasswordResetDialog() {}

    public static void open(SupabaseConfig config, String recoveryAccessToken) {
        JDialog dialog = new JDialog(
            (Window) null, "Reset your Feric password", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        IdeTheme.styleDialog(dialog);
        dialog.setLayout(new BorderLayout(0, 12));

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(IdeTheme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 18, 26));
        JLabel brand = new JLabel("FERIC  /  ACCOUNT SECURITY");
        brand.setForeground(IdeTheme.ACCENT);
        brand.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 10f));
        JLabel title = new JLabel("Choose a new password");
        title.setForeground(IdeTheme.TEXT);
        title.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 21f));
        JPanel heading = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        heading.setOpaque(false);
        heading.add(brand);
        heading.add(title);

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 1, 6, 7));
        fields.setOpaque(false);
        JPasswordField password = new JPasswordField(26);
        JPasswordField confirmation = new JPasswordField(26);
        JLabel status = new JLabel("Choose a new password for your account.");
        JLabel passwordLabel = new JLabel("New password (at least 6 characters)");
        passwordLabel.setForeground(IdeTheme.MUTED);
        passwordLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 11f));
        fields.add(passwordLabel);
        IdeTheme.styleTextField(password);
        fields.add(password);
        JLabel confirmationLabel = new JLabel("Confirm new password");
        confirmationLabel.setForeground(IdeTheme.MUTED);
        confirmationLabel.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 11f));
        fields.add(confirmationLabel);
        IdeTheme.styleTextField(confirmation);
        fields.add(confirmation);
        status.setForeground(IdeTheme.MUTED);
        status.setFont(IdeTheme.UI_FONT.deriveFont(11f));
        status.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        fields.add(status);

        JButton cancel = new JButton("Cancel");
        JButton update = new JButton("Update password");
        IdeTheme.styleButton(cancel);
        IdeTheme.stylePrimaryButton(update);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 20, 16, 20));
        actions.add(cancel);
        actions.add(update);
        content.add(heading, BorderLayout.NORTH);
        content.add(fields, BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);

        cancel.addActionListener(event -> dialog.dispose());
        update.addActionListener(event -> {
            if ("Done".equals(update.getText())) {
                dialog.dispose();
            } else {
                updatePassword(
                    dialog, config, recoveryAccessToken, password, confirmation,
                    status, update, cancel);
            }
        });
        dialog.getRootPane().setDefaultButton(update);
        dialog.pack();
        dialog.setMinimumSize(new java.awt.Dimension(450, dialog.getHeight()));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static void updatePassword(
            JDialog dialog,
            SupabaseConfig config,
            String recoveryAccessToken,
            JPasswordField passwordField,
            JPasswordField confirmationField,
            JLabel status,
            JButton update,
            JButton cancel) {
        char[] password = passwordField.getPassword();
        char[] confirmation = confirmationField.getPassword();
        if (password.length < 6) {
            clear(password, confirmation);
            status.setText("Use a password with at least 6 characters.");
            passwordField.requestFocusInWindow();
            return;
        }
        if (!Arrays.equals(password, confirmation)) {
            clear(password, confirmation);
            status.setText("The passwords do not match.");
            confirmationField.requestFocusInWindow();
            return;
        }
        Arrays.fill(confirmation, '\0');
        passwordField.setText("");
        confirmationField.setText("");
        update.setEnabled(false);
        cancel.setEnabled(false);
        status.setText("Updating your password securely…");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    new SupabaseClient(config).updatePassword(recoveryAccessToken, password);
                    return null;
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    status.setText("Password updated. Return to Feric and sign in.");
                    update.setText("Done");
                    update.setEnabled(true);
                    cancel.setVisible(false);
                    passwordField.setVisible(false);
                    confirmationField.setVisible(false);
                    dialog.pack();
                    dialog.setLocationRelativeTo(null);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    update.setEnabled(true);
                    cancel.setEnabled(true);
                    status.setText("Password update was interrupted. Please try again.");
                } catch (ExecutionException exception) {
                    update.setEnabled(true);
                    cancel.setEnabled(true);
                    Throwable cause = exception.getCause();
                    status.setText(cause == null || cause.getMessage() == null
                        ? "Could not update the password." : cause.getMessage());
                }
            }
        }.execute();
    }

    private static void clear(char[] password, char[] confirmation) {
        Arrays.fill(password, '\0');
        Arrays.fill(confirmation, '\0');
    }
}
