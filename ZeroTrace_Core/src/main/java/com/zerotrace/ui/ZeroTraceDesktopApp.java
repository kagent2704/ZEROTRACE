package com.zerotrace.ui;

import com.zerotrace.core.EncryptionService;
import com.zerotrace.core.client.AuthClient;
import com.zerotrace.core.client.KeyManager;
import com.zerotrace.core.client.SessionManager;
import com.zerotrace.crypto.RSAUtil;
import com.zerotrace.model.AuthResponse;
import com.zerotrace.model.MessagePacket;
import com.zerotrace.model.MessageRequest;
import com.zerotrace.model.SendMessageResponse;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

public class ZeroTraceDesktopApp {

    private final KeyManager keyManager = new KeyManager();
    private final SessionManager sessionManager = new SessionManager();

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel root;

    private JTextField serverField;
    private JTextField loginUserField;
    private JPasswordField loginPasswordField;
    private JTextField registerUserField;
    private JPasswordField registerPasswordField;
    private JTextField registerOrgField;
    private JLabel authStatusLabel;

    private JLabel sessionLabel;
    private JLabel sessionMetaLabel;
    private JTextField receiverField;
    private JComboBox<String> modeCombo;
    private JSpinner ttlSpinner;
    private JButton adminButton;
    private JTextArea composeArea;
    private JTextArea transcriptArea;
    private JLabel actionStatusLabel;

    private SessionManager.Session session;
    private AuthClient authClient;

    public void launch() {
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("ZeroTrace");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(960, 640));

            cardLayout = new CardLayout();
            root = new JPanel(cardLayout);
            root.add(buildAuthPanel(), "auth");
            root.add(buildChatPanel(), "chat");

            frame.setContentPane(root);
            restoreSessionIfPresent();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private JPanel buildAuthPanel() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.setBackground(new Color(18, 24, 38));

        JLabel title = new JLabel("ZeroTrace Secure Messenger");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        panel.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Login", buildLoginTab());
        tabs.addTab("Register", buildRegisterTab());
        panel.add(tabs, BorderLayout.CENTER);

        authStatusLabel = new JLabel("Ready");
        authStatusLabel.setForeground(new Color(142, 199, 255));
        panel.add(authStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildLoginTab() {
        JPanel panel = formPanel();
        serverField = new JTextField(System.getenv().getOrDefault("ZEROTRACE_SERVER_URL", "http://localhost:8080"));
        loginUserField = new JTextField();
        loginPasswordField = new JPasswordField();

        panel.add(formLabel("Server URL"));
        panel.add(serverField);
        panel.add(formLabel("Username"));
        panel.add(loginUserField);
        panel.add(formLabel("Password"));
        panel.add(loginPasswordField);

        JButton loginButton = primaryButton("Login");
        loginButton.addActionListener(e -> authenticate(false));
        panel.add(new JLabel());
        panel.add(loginButton);
        return panel;
    }

    private JPanel buildRegisterTab() {
        JPanel panel = formPanel();
        registerUserField = new JTextField();
        registerPasswordField = new JPasswordField();
        registerOrgField = new JTextField("default-org");

        panel.add(formLabel("Username"));
        panel.add(registerUserField);
        panel.add(formLabel("Password"));
        panel.add(registerPasswordField);
        panel.add(formLabel("Organization"));
        panel.add(registerOrgField);

        JButton registerButton = primaryButton("Register");
        registerButton.addActionListener(e -> authenticate(true));
        panel.add(new JLabel());
        panel.add(registerButton);
        return panel;
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel top = new JPanel(new BorderLayout());
        JPanel sessionPanel = new JPanel(new GridLayout(0, 1));
        sessionLabel = new JLabel("Not signed in");
        sessionLabel.setFont(sessionLabel.getFont().deriveFont(Font.BOLD, 16f));
        sessionMetaLabel = new JLabel(" ");
        sessionPanel.add(sessionLabel);
        sessionPanel.add(sessionMetaLabel);
        top.add(sessionPanel, BorderLayout.WEST);

        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> logout());
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton("Request Audit Export");
        exportButton.addActionListener(e -> requestAuditExport());
        JButton downloadExportButton = new JButton("Export Approved Data");
        downloadExportButton.addActionListener(e -> exportApprovedAuditData());
        adminButton = new JButton("Admin Tools");
        adminButton.addActionListener(e -> openAdminTools());
        topActions.add(exportButton);
        topActions.add(downloadExportButton);
        topActions.add(adminButton);
        topActions.add(logoutButton);
        top.add(topActions, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        transcriptArea = new JTextArea();
        transcriptArea.setEditable(false);
        transcriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        panel.add(new JScrollPane(transcriptArea), BorderLayout.CENTER);

        JPanel composePanel = new JPanel(new BorderLayout(8, 8));

        JPanel controls = new JPanel(new GridLayout(1, 3, 8, 8));
        receiverField = new JTextField();
        modeCombo = new JComboBox<>(new String[]{"PRIVATE", "AUDIT"});
        ttlSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 86400, 5));
        modeCombo.addActionListener(e -> ttlSpinner.setEnabled("PRIVATE".equals(modeCombo.getSelectedItem())));
        JButton refreshButton = new JButton("Refresh Inbox");
        refreshButton.addActionListener(e -> refreshInbox());
        JButton sendButton = primaryButton("Send Secure Message");
        sendButton.addActionListener(e -> sendMessage());
        controls.add(labeledField("Receiver", receiverField));
        controls.add(labeledField("Mode / TTL", modeAndTtlPanel()));
        controls.add(refreshButton);

        composeArea = new JTextArea(6, 40);
        composeArea.setLineWrap(true);
        composeArea.setWrapStyleWord(true);

        actionStatusLabel = new JLabel("Authenticated session required for all messaging operations.");
        composePanel.add(controls, BorderLayout.NORTH);
        composePanel.add(new JScrollPane(composeArea), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(sendButton, BorderLayout.WEST);
        south.add(actionStatusLabel, BorderLayout.CENTER);
        composePanel.add(south, BorderLayout.SOUTH);

        panel.add(composePanel, BorderLayout.SOUTH);
        return panel;
    }

    private void authenticate(boolean register) {
        String serverUrl = serverField.getText().trim();
        String username = register ? registerUserField.getText().trim() : loginUserField.getText().trim();
        String password = register
                ? new String(registerPasswordField.getPassword()).trim()
                : new String(loginPasswordField.getPassword()).trim();
        String organization = register ? registerOrgField.getText().trim() : null;

        runAsync(() -> {
            KeyPair keyPair = keyManager.loadOrCreate(username);
            AuthClient client = new AuthClient(serverUrl);
            AuthResponse response = register
                    ? client.register(username, password, keyManager.getPublicKeyString(keyPair), organization == null || organization.isBlank() ? "default-org" : organization)
                    : client.login(username, password, keyManager.getPublicKeyString(keyPair));

            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }

            sessionManager.save(
                    username,
                    serverUrl,
                    response.getToken(),
                    response.getOrganization(),
                    response.getRole(),
                    response.isAuditModeApproved()
            );
            return new SessionContext(
                    new SessionManager.Session(
                            username,
                            serverUrl,
                            response.getToken(),
                            response.getOrganization(),
                            response.getRole(),
                            response.isAuditModeApproved()
                    ),
                    client
            );
        }, context -> {
            this.session = context.session();
            this.authClient = context.client();
            switchToChat("Authenticated as " + session.username());
        }, error -> authStatusLabel.setText(error.getMessage()));
    }

    private void sendMessage() {
        String receiver = receiverField.getText().trim();
        String message = composeArea.getText().trim();
        String mode = (String) modeCombo.getSelectedItem();
        Integer ttlSeconds = "AUDIT".equals(mode) ? null : (Integer) ttlSpinner.getValue();
        if (receiver.isEmpty() || message.isEmpty()) {
            actionStatusLabel.setText("Receiver and message are required.");
            return;
        }

        runAsync(() -> {
            KeyPair senderKeys = keyManager.loadOrCreate(session.username());
            PublicKey receiverPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(receiver));
            MessagePacket packet = EncryptionService.encryptForRelay(
                    session.username(),
                    receiver,
                    message,
                    senderKeys,
                    receiverPublicKey
            );

            return authClient.sendMessage(new MessageRequest(
                    packet.getSender(),
                    packet.getReceiver(),
                    packet.getEncryptedMessage(),
                    packet.getEncryptedAESKey(),
                    packet.getSignature(),
                    packet.getIv(),
                    mode,
                    ttlSeconds
            ));
        }, response -> {
            composeArea.setText("");
            actionStatusLabel.setText(renderThreatStatus(response));
            transcriptArea.append("me -> " + receiver + " [" + mode + "]: " + message + System.lineSeparator());
        }, error -> actionStatusLabel.setText(error.getMessage()));
    }

    private void refreshInbox() {
        runAsync(() -> {
            KeyPair receiverKeys = keyManager.loadOrCreate(session.username());
            List<MessagePacket> messages = authClient.fetchInbox(session.username());
            StringBuilder builder = new StringBuilder();
            for (MessagePacket packet : messages) {
                PublicKey senderPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(packet.getSender()));
                try {
                    String plaintext = EncryptionService.decryptFromRelay(packet, receiverKeys.getPrivate(), senderPublicKey);
                    builder.append("[")
                            .append(packet.getMode())
                            .append(packet.getTtlSeconds() == null ? "" : " TTL=" + packet.getTtlSeconds() + "s")
                            .append("] ")
                            .append(packet.getSender())
                            .append(": ")
                            .append(plaintext)
                            .append(System.lineSeparator());
                } catch (Exception exception) {
                    builder.append(packet.getSender())
                            .append(": rejected tampered message (")
                            .append(exception.getMessage())
                            .append(')')
                            .append(System.lineSeparator());
                }
            }
            return builder.toString();
        }, messages -> {
            if (messages.isBlank()) {
                actionStatusLabel.setText("No new messages.");
                return;
            }
            transcriptArea.append(messages);
            actionStatusLabel.setText("Inbox refreshed.");
        }, error -> actionStatusLabel.setText(error.getMessage()));
    }

    private void requestAuditExport() {
        String input = JOptionPane.showInputDialog(frame, "Export lookback days (1-7)", "7");
        if (input == null || input.isBlank()) {
            return;
        }
        int lookbackDays = Integer.parseInt(input.trim());
        runAsync(() -> authClient.createExportRequest(lookbackDays),
                response -> actionStatusLabel.setText("Export request " + response.getRequestId() + " is " + response.getStatus()),
                error -> actionStatusLabel.setText(error.getMessage()));
    }

    private void exportApprovedAuditData() {
        runAsync(() -> authClient.myExportRequests(), responses -> {
            var approvedRequests = responses.stream()
                    .filter(response -> "APPROVED".equalsIgnoreCase(response.getStatus()))
                    .toList();
            if (approvedRequests.isEmpty()) {
                actionStatusLabel.setText("No approved export requests are ready.");
                return;
            }

            StringBuilder builder = new StringBuilder();
            for (var response : approvedRequests) {
                builder.append(response.getRequestId())
                        .append(": ")
                        .append(response.getDetail())
                        .append(" | expires ")
                        .append(response.getExpiresAt())
                        .append("\n");
            }

            String input = JOptionPane.showInputDialog(frame, "Approved exports:\n" + builder + "\nEnter request id to export");
            if (input == null || input.isBlank()) {
                return;
            }

            long requestId = Long.parseLong(input.trim());
            runAsync(() -> {
                Map<String, Object> export = authClient.exportAuditData(requestId);
                Path exportDirectory = Path.of(System.getProperty("user.home"), ".zerotrace", "exports");
                Files.createDirectories(exportDirectory);
                Path outputFile = exportDirectory.resolve("audit-export-" + requestId + ".json");
                authClient.writeJson(outputFile, export);
                return outputFile;
            }, outputFile -> actionStatusLabel.setText("Export saved to " + outputFile),
                    error -> actionStatusLabel.setText(error.getMessage()));
        }, error -> actionStatusLabel.setText(error.getMessage()));
    }

    private void openAdminTools() {
        String[] actions = {"Audit Access", "Review Export Requests"};
        String action = (String) JOptionPane.showInputDialog(
                frame,
                "Choose admin action",
                "Admin Tools",
                JOptionPane.PLAIN_MESSAGE,
                null,
                actions,
                actions[0]
        );

        if (action == null) {
            return;
        }

        if ("Audit Access".equals(action)) {
            String username = JOptionPane.showInputDialog(frame, "Username to review for audit mode");
            if (username == null || username.isBlank()) {
                return;
            }
            int choice = JOptionPane.showConfirmDialog(frame, "Grant audit mode to " + username.trim() + "?", "Audit Access", JOptionPane.YES_NO_OPTION);
            boolean approved = choice == JOptionPane.YES_OPTION;
            runAsync(() -> {
                authClient.setAuditApproval(username.trim(), approved);
                return approved ? "Audit mode approved for " + username.trim() : "Audit mode revoked for " + username.trim();
            }, message -> actionStatusLabel.setText(message), error -> actionStatusLabel.setText(error.getMessage()));
            return;
        }

        runAsync(() -> authClient.pendingExportRequests(), responses -> {
            if (responses.isEmpty()) {
                actionStatusLabel.setText("No pending export requests.");
                return;
            }
            StringBuilder builder = new StringBuilder();
            for (var response : responses) {
                builder.append(response.getRequestId()).append(": ").append(response.getDetail()).append("\n");
            }
            String requestIdInput = JOptionPane.showInputDialog(frame, "Pending requests:\n" + builder + "\nEnter request id to review");
            if (requestIdInput == null || requestIdInput.isBlank()) {
                return;
            }
            long requestId = Long.parseLong(requestIdInput.trim());
            int choice = JOptionPane.showConfirmDialog(frame, "Approve export request " + requestId + "?", "Export Approval", JOptionPane.YES_NO_OPTION);
            boolean approved = choice == JOptionPane.YES_OPTION;
            runAsync(() -> {
                authClient.approveExportRequest(requestId, approved);
                return approved ? "Approved export request " + requestId : "Rejected export request " + requestId;
            }, message -> actionStatusLabel.setText(message), error -> actionStatusLabel.setText(error.getMessage()));
        }, error -> actionStatusLabel.setText(error.getMessage()));
    }

    private void restoreSessionIfPresent() {
        try {
            sessionManager.load().ifPresent(savedSession -> {
                this.session = savedSession;
                this.authClient = new AuthClient(savedSession.serverUrl(), savedSession.token());
                switchToChat("Restored session for " + savedSession.username());
            });
        } catch (Exception exception) {
            authStatusLabel.setText("Session restore failed: " + exception.getMessage());
        }
    }

    private void logout() {
        try {
            sessionManager.clear();
        } catch (Exception ignored) {
        }
        session = null;
        authClient = null;
        transcriptArea.setText("");
        composeArea.setText("");
        authStatusLabel.setText("Signed out");
        cardLayout.show(root, "auth");
    }

    private void switchToChat(String status) {
        sessionLabel.setText("Signed in as " + session.username() + " via " + session.serverUrl());
        sessionMetaLabel.setText("Org: " + blankToFallback(session.organization(), "default-org")
                + " | Role: " + blankToFallback(session.role(), "USER")
                + " | Audit mode: " + (session.auditModeApproved() ? "approved" : "not approved"));
        adminButton.setEnabled("ADMIN".equalsIgnoreCase(blankToFallback(session.role(), "USER")));
        actionStatusLabel.setText(status);
        cardLayout.show(root, "chat");
    }

    private String renderThreatStatus(SendMessageResponse response) {
        if (response.getThreatAnalysis() == null) {
            return "Message sent.";
        }
        return "Message " + response.getMessageId() + ": "
                + response.getThreatAnalysis().getVerdict()
                + " - "
                + response.getThreatAnalysis().getDetail();
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        return panel;
    }

    private JLabel formLabel(String text) {
        return new JLabel(text);
    }

    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(25, 108, 255));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
    }

    private JPanel modeAndTtlPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 4, 4));
        panel.add(modeCombo);
        panel.add(ttlSpinner);
        return panel;
    }

    private JPanel labeledField(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private <T> void runAsync(TaskSupplier<T> supplier, TaskSuccess<T> success, TaskError error) {
        actionStatusLabelSafe("Working...");
        authStatusLabelSafe("Working...");
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return supplier.get();
            }

            @Override
            protected void done() {
                try {
                    success.accept(get());
                } catch (Exception exception) {
                    error.accept(exception);
                }
            }
        };
        worker.execute();
    }

    private void actionStatusLabelSafe(String text) {
        if (actionStatusLabel != null) {
            actionStatusLabel.setText(text);
        }
    }

    private void authStatusLabelSafe(String text) {
        if (authStatusLabel != null) {
            authStatusLabel.setText(text);
        }
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SessionContext(SessionManager.Session session, AuthClient client) {
    }

    @FunctionalInterface
    private interface TaskSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface TaskSuccess<T> {
        void accept(T value);
    }

    @FunctionalInterface
    private interface TaskError {
        void accept(Exception exception);
    }
}
