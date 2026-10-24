package com.classroom.live;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

public final class SupabaseAuthDialog {
    private SupabaseAuthDialog() {}

    public static SupabaseClient authenticate(Window owner) {
        SupabaseClient[] authenticated = new SupabaseClient[1];
        JDialog dialog = new JDialog(owner, "Sign in to Feric", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        IdeTheme.styleDialog(dialog);
        dialog.setLayout(new BorderLayout(0, 8));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(IdeTheme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));
        JLabel brand = new JLabel("FERIC  /  CLASSROOM");
        brand.setForeground(IdeTheme.ACCENT);
        brand.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 10f));
        JLabel title = new JLabel("Welcome back");
        title.setForeground(IdeTheme.TEXT);
        title.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Sign in or create an account to continue.");
        subtitle.setForeground(IdeTheme.MUTED);
        subtitle.setFont(IdeTheme.UI_FONT.deriveFont(12f));
        JPanel heading = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
        heading.setOpaque(false);
        heading.add(brand);
        heading.add(title);
        heading.add(subtitle);

        JPanel fields = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        fields.setOpaque(false);
        JTextField email = new JTextField(24);
        JPasswordField password = new JPasswordField(24);
        JLabel message = new JLabel("Use your Supabase Auth email and password.");
        JLabel emailLabel = new JLabel("Email");
        styleAuthLabel(emailLabel);
        fields.add(emailLabel);
        IdeTheme.styleTextField(email);
        email.setToolTipText("Enter the email address for your Feric account");
        fields.add(email);
        JLabel passwordLabel = new JLabel("Password");
        styleAuthLabel(passwordLabel);
        fields.add(passwordLabel);
        IdeTheme.styleTextField(password);
        fields.add(password);
        message.setForeground(IdeTheme.MUTED);
        message.setFont(IdeTheme.UI_FONT.deriveFont(11f));
        message.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        fields.add(message);

        JButton signIn = new JButton("Sign in");
        JButton register = new JButton("Create account");
        JButton forgotPassword = new JButton("Forgot password?");
        JButton cancel = new JButton("Cancel");
        IdeTheme.stylePrimaryButton(signIn);
        IdeTheme.styleButton(register);
        IdeTheme.styleButton(cancel);
        forgotPassword.setFont(IdeTheme.UI_FONT);
        forgotPassword.setForeground(IdeTheme.ACCENT);
        forgotPassword.setBackground(IdeTheme.BACKGROUND);
        forgotPassword.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        forgotPassword.setFocusPainted(false);
        forgotPassword.setOpaque(false);
        JPanel secondaryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        secondaryActions.setOpaque(false);
        secondaryActions.add(forgotPassword);
        JPanel actions = new JPanel(new BorderLayout(8, 4));
        actions.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(register);
        buttons.add(signIn);
        actions.add(secondaryActions, BorderLayout.WEST);
        actions.add(buttons, BorderLayout.EAST);
        content.add(heading, BorderLayout.NORTH);
        content.add(fields, BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);

        signIn.addActionListener(event -> authenticate(
            dialog, email, password, message, signIn, register, false, authenticated));
        register.addActionListener(event -> authenticate(
            dialog, email, password, message, signIn, register, true, authenticated));
        forgotPassword.addActionListener(event ->
            requestPasswordReset(email, message, signIn, register, forgotPassword));
        cancel.addActionListener(event -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(signIn);
        dialog.pack();
        dialog.setMinimumSize(new java.awt.Dimension(420, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return authenticated[0];
    }

    private static void styleAuthLabel(JLabel label) {
        label.setForeground(IdeTheme.MUTED);
        label.setFont(IdeTheme.UI_FONT.deriveFont(Font.BOLD, 11f));
    }

    private static void authenticate(
            JDialog dialog,
            JTextField email,
            JPasswordField password,
            JLabel message,
            JButton signIn,
            JButton register,
            boolean createAccount,
            SupabaseClient[] authenticated) {
        String address = email.getText().trim();
        char[] secret = password.getPassword();
        if (address.isBlank() || secret.length == 0) {
            message.setText("Enter both an email address and password.");
            return;
        }
        signIn.setEnabled(false);
        register.setEnabled(false);
        message.setText(createAccount ? "Creating account…" : "Signing in…");

        new SwingWorker<SupabaseClient, Void>() {
            @Override
            protected SupabaseClient doInBackground() throws Exception {
                SupabaseClient client = new SupabaseClient(SupabaseConfig.load());
                if (createAccount) {
                    JsonNode response = client.signUp(address, secret);
                    if (!response.hasNonNull("access_token")) {
                        throw new IOException(
                            "Account created. Confirm the email address, then sign in here.");
                    }
                    return client;
                }
                client.signIn(address, secret);
                return client;
            }

            @Override
            protected void done() {
                java.util.Arrays.fill(secret, '\0');
                signIn.setEnabled(true);
                register.setEnabled(true);
                try {
                    authenticated[0] = get();
                    dialog.dispose();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    message.setText("Authentication was interrupted.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    message.setText(cause == null || cause.getMessage() == null
                        ? "Authentication failed." : cause.getMessage());
                }
            }
        }.execute();
    }

    private static void requestPasswordReset(
            JTextField email,
            JLabel message,
            JButton signIn,
            JButton register,
            JButton forgotPassword) {
        String address = email.getText().trim();
        if (address.isBlank()) {
            message.setText("Enter your email address to request a reset link.");
            return;
        }
        signIn.setEnabled(false);
        register.setEnabled(false);
        forgotPassword.setEnabled(false);
        message.setText("Requesting password reset…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                SupabaseConfig config = SupabaseConfig.load();
                PasswordResetServer server = PasswordResetServer.start(config);
                new SupabaseClient(config).requestPasswordReset(address, server.redirectUri());
                return null;
            }

            @Override
            protected void done() {
                signIn.setEnabled(true);
                register.setEnabled(true);
                forgotPassword.setEnabled(true);
                try {
                    get();
                    message.setText(
                        "If the account exists, check your email. Keep Feric open and open "
                            + "the link on this computer.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    message.setText("Password reset request was interrupted.");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    message.setText(cause == null || cause.getMessage() == null
                        ? "Could not request a password reset." : cause.getMessage());
                }
            }
        }.execute();
    }
}
