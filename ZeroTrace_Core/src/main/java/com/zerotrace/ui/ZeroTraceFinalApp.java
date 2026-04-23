package com.zerotrace.ui;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import com.zerotrace.core.EncryptionService;
import com.zerotrace.core.safety.TextSafetyInspector;
import com.zerotrace.core.client.AuthClient;
import com.zerotrace.core.client.KeyManager;
import com.zerotrace.core.client.SessionManager;
import com.zerotrace.crypto.RSAUtil;
import com.zerotrace.model.AuthResponse;
import com.zerotrace.model.AuditExportResponse;
import com.zerotrace.model.MessagePacket;
import com.zerotrace.model.MessageRequest;
import com.zerotrace.model.SendMessageResponse;
import com.zerotrace.model.ThreatAnalysisResult;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class ZeroTraceFinalApp extends Application {
    private static final String VIEW_OPEN_CHATS = "OPEN_CHATS";
    private static final String VIEW_CHAT = "CHAT";
    private static final String VIEW_OTHER = "OTHER";

    private final KeyManager keyManager = new KeyManager();
    private final SessionManager sessionManager = new SessionManager();
    private final ObservableList<String> contacts = FXCollections.observableArrayList();
    private final Map<String, List<ChatEntry>> chatHistory = new LinkedHashMap<>();

    private Stage primaryStage;
    private BorderPane mainRoot;
    private VBox peoplePanel;
    private VBox contentPanel;
    private VBox activeChatContainer;
    private VBox activeMessagesBox;
    private String activeChatViewContact;
    private String activeView = VIEW_OPEN_CHATS;
    private Label topUserLabel;
    private Label topStatusLabel;
    private Timeline ttlSweepTimeline;

    private SessionManager.Session session;
    private AuthClient authClient;
    private String selectedContact;
    private boolean isDarkTheme = true;
    private static final String D_BG = "#1a1f2e";
    private static final String D_SURFACE = "#242938";
    private static final String D_SURFACE2 = "#2e3447";
    private static final String D_BORDER = "#3a4060";
    private static final String D_TEXT1 = "#e8eaf6";
    private static final String D_TEXT2 = "#8892b0";
    private static final String D_MUTED = "#4a5280";
    private static final String D_ACCENT = "#4dd0e1";
    private static final String D_ASOFT = "#4dd0e133";
    private static final String D_ADIM = "#4dd0e188";
    private static final String D_BSENT = "#1565c0";
    private static final String D_BRECV = "#2e3447";
    private static final String D_ONLINE = "#4dd0e1";
    private static final String D_DANGER = "#ef5350";

    private static final String L_BG = "#f0f4f8";
    private static final String L_SURFACE = "#ffffff";
    private static final String L_SURFACE2 = "#e8eef5";
    private static final String L_BORDER = "#c5d0de";
    private static final String L_TEXT1 = "#0d1b2a";
    private static final String L_TEXT2 = "#3a5068";
    private static final String L_MUTED = "#7a95ab";
    private static final String L_ACCENT = "#1565c0";
    private static final String L_ASOFT = "#1565c022";
    private static final String L_ADIM = "#1565c099";
    private static final String L_BSENT = "#1565c0";
    private static final String L_BRECV = "#e8eef5";
    private static final String L_ONLINE = "#1565c0";
    private static final String L_DANGER = "#c62828";

    public static void launchApp(String[] args) {
        Application.launch(ZeroTraceFinalApp.class, args);
    }

    private String bg() { return isDarkTheme ? D_BG : L_BG; }
    private String surf() { return isDarkTheme ? D_SURFACE : L_SURFACE; }
    private String surf2() { return isDarkTheme ? D_SURFACE2 : L_SURFACE2; }
    private String bord() { return isDarkTheme ? D_BORDER : L_BORDER; }
    private String t1() { return isDarkTheme ? D_TEXT1 : L_TEXT1; }
    private String t2() { return isDarkTheme ? D_TEXT2 : L_TEXT2; }
    private String tm() { return isDarkTheme ? D_MUTED : L_MUTED; }
    private String acc() { return isDarkTheme ? D_ACCENT : L_ACCENT; }
    private String asoft() { return isDarkTheme ? D_ASOFT : L_ASOFT; }
    private String adim() { return isDarkTheme ? D_ADIM : L_ADIM; }
    private String bsent() { return isDarkTheme ? D_BSENT : L_BSENT; }
    private String brecv() { return isDarkTheme ? D_BRECV : L_BRECV; }
    private String online() { return isDarkTheme ? D_ONLINE : L_ONLINE; }
    private String danger() { return isDarkTheme ? D_DANGER : L_DANGER; }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("ZeroTrace");
        restoreSession();
        primaryStage.show();
        primaryStage.setMaximized(true);
    }

    private void restoreSession() {
        try {
            Optional<SessionManager.Session> saved = sessionManager.load();
            if (saved.isPresent()) {
                session = saved.get();
                authClient = new AuthClient(session.serverUrl(), session.token());
                showHomePage();
                refreshInbox(false);
                return;
            }
        } catch (Exception ignored) {
        }
        showLoginPage();
    }

    private void showLoginPage() {
        VBox root = new VBox();
        root.setStyle("-fx-background-color:" + bg() + ";");
        root.setAlignment(Pos.CENTER);

        VBox logoArea = new VBox(8);
        logoArea.setAlignment(Pos.CENTER);
        logoArea.setPadding(new Insets(0, 0, 36, 0));
        logoArea.getChildren().addAll(
                mk("ZeroTrace", FontWeight.BOLD, 42, acc()),
                mk("Secure. Private. Zero Trace.", FontWeight.NORMAL, 14, tm())
        );

        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(36));
        card.setMaxWidth(460);
        card.setStyle("-fx-background-color:" + surf() + ";-fx-background-radius:12;"
                + "-fx-border-color:" + asoft() + ";-fx-border-radius:12;-fx-border-width:1;");

        TextField serverField = new TextField(System.getenv().getOrDefault("ZEROTRACE_SERVER_URL", "http://localhost:8080"));
        TextField userField = new TextField();
        PasswordField passField = new PasswordField();
        TextField orgField = new TextField("default-org");
        styleField(serverField);
        styleField(userField);
        stylePassField(passField);
        styleField(orgField);
        serverField.setPromptText("Server URL");
        userField.setPromptText("Enter username");
        passField.setPromptText("Enter password");
        orgField.setPromptText("Organization for registration");

        Label statusLabel = mk("", FontWeight.NORMAL, 11, danger());

        Button loginBtn = accentBtn("LOGIN", 14);
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setOnAction(event -> authenticate(serverField, userField, passField, orgField, statusLabel, false));

        Button registerBtn = secondaryBtn("REGISTER", 14);
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setOnAction(event -> authenticate(serverField, userField, passField, orgField, statusLabel, true));

        HBox actionRow = new HBox(10, loginBtn, registerBtn);
        HBox.setHgrow(loginBtn, Priority.ALWAYS);
        HBox.setHgrow(registerBtn, Priority.ALWAYS);

        card.getChildren().addAll(
                rowRight(themeToggleBtn()),
                mk("Login or register to continue", FontWeight.BOLD, 16, t1()),
                fldLbl("SERVER"), serverField,
                fldLbl("USERNAME"), userField,
                fldLbl("PASSWORD"), passField,
                fldLbl("ORGANIZATION (used for registration)"), orgField,
                statusLabel,
                actionRow
        );

        root.getChildren().addAll(logoArea, card, footer());
        primaryStage.setScene(new Scene(root, 1440, 900));
    }

    private void authenticate(
            TextField serverField,
            TextField userField,
            PasswordField passField,
            TextField orgField,
            Label statusLabel,
            boolean register
    ) {
        String serverUrl = serverField.getText().trim();
        String username = userField.getText().trim();
        String password = passField.getText().trim();
        String organization = orgField.getText().trim().isEmpty() ? "default-org" : orgField.getText().trim();

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Server, username, and password are required.");
            return;
        }

        runTask(() -> {
            KeyPair keyPair = keyManager.loadOrCreate(username);
            AuthClient client = new AuthClient(serverUrl);
            AuthResponse response = register
                    ? client.register(username, password, keyManager.getPublicKeyString(keyPair), organization)
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

            return new LoginContext(
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
        }, contextObj -> {
            LoginContext context = (LoginContext) contextObj;
            session = context.session();
            authClient = context.authClient();
            contacts.clear();
            chatHistory.clear();
            selectedContact = null;
            showHomePage();
            refreshInbox(false);
        }, error -> statusLabel.setText(error.getMessage()));
    }

    private void showHomePage() {
        mainRoot = new BorderPane();
        mainRoot.setStyle("-fx-background-color:" + bg() + ";");
        mainRoot.setTop(buildTopBar());

        HBox body = new HBox(0);
        body.setFillHeight(true);

        peoplePanel = buildPeopleList();
        contentPanel = buildOpenChatsContent();
        activeView = VIEW_OPEN_CHATS;
        HBox.setHgrow(contentPanel, Priority.ALWAYS);

        body.getChildren().addAll(buildNavRail(), peoplePanel, contentPanel);
        mainRoot.setCenter(body);

        primaryStage.setScene(new Scene(mainRoot, 1440, 900));
        startTtlSweep();
    }

    private HBox buildTopBar() {
        HBox tb = new HBox(12);
        tb.setPadding(new Insets(12, 24, 12, 24));
        tb.setAlignment(Pos.CENTER_LEFT);
        tb.setStyle("-fx-background-color:" + surf() + ";"
                + "-fx-border-color:" + acc() + ";-fx-border-width:0 0 2 0;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = secondaryBtn("Refresh Inbox", 11);
        refreshBtn.setOnAction(event -> refreshInbox(true));

        Button logoutBtn = secondaryBtn("Logout", 11);
        logoutBtn.setOnAction(event -> logout());

        topStatusLabel = mk("Connected", FontWeight.NORMAL, 11, tm());
        topUserLabel = mk("● " + session.username(), FontWeight.NORMAL, 12, online());

        tb.getChildren().addAll(
                mk("ZeroTrace", FontWeight.BOLD, 20, acc()),
                topStatusLabel,
                spacer,
                refreshBtn,
                themeToggleBtn(),
                topUserLabel,
                logoutBtn
        );
        return tb;
    }

    private VBox buildNavRail() {
        VBox rail = new VBox(2);
        rail.setPrefWidth(120);
        rail.setMinWidth(120);
        rail.setPadding(new Insets(14, 0, 14, 0));
        rail.setStyle("-fx-background-color:" + surf() + ";"
                + "-fx-border-color:" + bord() + ";-fx-border-width:0 1 0 0;");

        Label mini = mk("ZT", FontWeight.BOLD, 18, acc());
        mini.setPadding(new Insets(4, 0, 16, 0));
        mini.setMaxWidth(Double.MAX_VALUE);
        mini.setAlignment(Pos.CENTER);
        rail.getChildren().add(mini);

        String[] items = {"Open Chats", "User Profile", "Encryption", "Governance", "Settings"};
        for (String item : items) {
            rail.getChildren().add(navItem(item));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        rail.getChildren().add(spacer);

        Label logoutLbl = mk("Logout", FontWeight.BOLD, 11, danger());
        logoutLbl.setPadding(new Insets(8, 0, 4, 0));
        logoutLbl.setMaxWidth(Double.MAX_VALUE);
        logoutLbl.setAlignment(Pos.CENTER);
        logoutLbl.setStyle("-fx-cursor:hand;");
        logoutLbl.setOnMouseClicked(event -> logout());
        rail.getChildren().add(logoutLbl);
        return rail;
    }

    private VBox navItem(String label) {
        VBox item = new VBox(5);
        item.setAlignment(Pos.CENTER);
        item.setPadding(new Insets(12, 8, 12, 8));
        item.setMaxWidth(Double.MAX_VALUE);
        item.setStyle("-fx-cursor:hand;-fx-background-radius:6;");

        Label textLbl = mk(label, FontWeight.BOLD, 10, t2());
        textLbl.setWrapText(true);
        textLbl.setAlignment(Pos.CENTER);
        textLbl.setMaxWidth(Double.MAX_VALUE);
        item.getChildren().add(textLbl);

        item.setOnMouseEntered(event -> item.setStyle("-fx-cursor:hand;-fx-background-color:" + asoft() + ";-fx-background-radius:6;"));
        item.setOnMouseExited(event -> item.setStyle("-fx-cursor:hand;-fx-background-radius:6;"));
        item.setOnMouseClicked(event -> handleNavClick(label));
        return item;
    }

    private void handleNavClick(String label) {
        switch (label) {
            case "Open Chats" -> showOpenChatsView();
            case "User Profile" -> showNonChatView(buildProfilePanel());
            case "Encryption" -> showNonChatView(buildEncryptionPanel());
            case "Governance" -> showNonChatView(buildGovernancePanel());
            case "Settings" -> showNonChatView(buildSettingsPanel());
            default -> showOpenChatsView();
        }
    }

    private VBox buildPeopleList() {
        VBox panel = new VBox(0);
        panel.setPrefWidth(240);
        panel.setMinWidth(210);
        panel.setStyle("-fx-background-color:" + surf() + ";"
                + "-fx-border-color:" + bord() + ";-fx-border-width:0 1 0 0;");

        HBox header = new HBox();
        header.setPadding(new Insets(14, 14, 10, 14));
        header.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(mk("CONTACTS", FontWeight.BOLD, 11, tm()), spacer, mk(contacts.size() + " chats", FontWeight.NORMAL, 10, tm()));

        Button newChatBtn = secondaryBtn("New Chat", 11);
        newChatBtn.setMaxWidth(Double.MAX_VALUE);
        newChatBtn.setOnAction(event -> promptForContact());
        VBox newChatBox = new VBox(newChatBtn);
        newChatBox.setPadding(new Insets(0, 10, 10, 10));

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");

        VBox list = new VBox(0);
        if (contacts.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setPadding(new Insets(18));
            empty.getChildren().addAll(
                    mk("No active chats yet.", FontWeight.BOLD, 12, t1()),
                    mk("Use New Chat to start a secure conversation.", FontWeight.NORMAL, 11, tm())
            );
            list.getChildren().add(empty);
        } else {
            for (String contact : contacts) {
                list.getChildren().add(peopleRow(contact, contact.equals(selectedContact)));
            }
        }
        scroll.setContent(list);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(header, newChatBox, sep(), scroll);
        return panel;
    }

    private HBox peopleRow(String name, boolean selected) {
        ChatEntry latest = latestMessage(name);
        String preview = latest == null ? "No messages yet" : latest.message();
        HBox row = new HBox(10);
        row.setPadding(new Insets(10, 12, 10, 12));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(selected
                ? "-fx-background-color:" + asoft() + ";-fx-cursor:hand;"
                : "-fx-background-color:transparent;-fx-cursor:hand;");

        StackPane avatar = avatar(name, 38, 14);
        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        info.getChildren().addAll(
                mk(name, FontWeight.BOLD, 13, selected ? acc() : t1()),
                mk(preview, FontWeight.NORMAL, 11, tm())
        );

        row.getChildren().addAll(avatar, info);
        row.setOnMouseEntered(event -> {
            if (!selected) {
                row.setStyle("-fx-background-color:" + surf2() + ";-fx-cursor:hand;");
            }
        });
        row.setOnMouseExited(event -> {
            if (!selected) {
                row.setStyle("-fx-background-color:transparent;-fx-cursor:hand;");
            }
        });
        row.setOnMouseClicked(event -> showChatPage(name));
        return row;
    }

    private VBox buildOpenChatsContent() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(30));
        pane.setStyle("-fx-background-color:" + bg() + ";");
        pane.getChildren().addAll(
                mk("Open Chats", FontWeight.BOLD, 22, t1()),
                mk("Select a contact to start or continue a secure conversation.", FontWeight.NORMAL, 13, t2()),
                sep()
        );

        if (contacts.isEmpty()) {
            pane.getChildren().add(mk("No chats yet. Start with New Chat on the left.", FontWeight.NORMAL, 13, tm()));
            return pane;
        }

        for (String contact : contacts) {
            pane.getChildren().add(openChatCard(contact));
        }
        return pane;
    }

    private HBox openChatCard(String contact) {
        ChatEntry latest = latestMessage(contact);
        String lastMessage = latest == null ? "No messages yet" : latest.message();
        String time = latest == null ? "" : latest.createdAt();

        HBox card = new HBox(14);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setAlignment(Pos.CENTER_LEFT);
        String base = "-fx-background-color:" + surf() + ";-fx-background-radius:10;"
                + "-fx-border-color:" + bord() + ";-fx-border-radius:10;-fx-border-width:1;-fx-cursor:hand;";
        String hover = "-fx-background-color:" + surf2() + ";-fx-background-radius:10;"
                + "-fx-border-color:" + adim() + ";-fx-border-radius:10;-fx-border-width:1;-fx-cursor:hand;";
        card.setStyle(base);
        card.setOnMouseEntered(event -> card.setStyle(hover));
        card.setOnMouseExited(event -> card.setStyle(base));
        card.setOnMouseClicked(event -> showChatPage(contact));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(mk(contact, FontWeight.BOLD, 14, t1()), spacer, mk(time, FontWeight.NORMAL, 10, tm()));
        info.getChildren().addAll(top, mk(lastMessage, FontWeight.NORMAL, 12, t2()));

        card.getChildren().addAll(avatar(contact, 44, 17), info);
        return card;
    }

    private void showChatPage(String contactName) {
        selectedContact = contactName;
        addContact(contactName);
        refreshPeoplePanel();
        activeView = VIEW_CHAT;
        setContent(buildChatArea(contactName));
        if (session != null && authClient != null) {
            refreshInbox(false);
        }
    }

    private VBox buildChatArea(String contactName) {
        activeChatViewContact = contactName;
        VBox chatArea = new VBox();
        activeChatContainer = chatArea;
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        chatArea.setStyle("-fx-background-color:" + bg() + ";");

        HBox header = new HBox(10);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:" + surf() + ";"
                + "-fx-border-color:" + bord() + ";-fx-border-width:0 0 1 0;");

        Label ttlLabel = mk("Secure relay", FontWeight.NORMAL, 11, adim());
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        header.getChildren().addAll(
                avatar(contactName, 36, 14),
                column(mk(contactName, FontWeight.BOLD, 15, t1()), mk("AES-256 End-to-End Encrypted", FontWeight.NORMAL, 10, adim())),
                headerSpacer,
                ttlLabel
        );

        VBox messagesBox = new VBox(10);
        messagesBox.setPadding(new Insets(16));
        activeMessagesBox = messagesBox;
        renderConversation(messagesBox, contactName);

        ScrollPane scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background:" + bg() + ";-fx-background-color:" + bg() + ";");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        ComboBox<String> modeBox = new ComboBox<>(FXCollections.observableArrayList("PRIVATE", "AUDIT"));
        modeBox.setValue("PRIVATE");
        styleCombo(modeBox);

        Spinner<Integer> ttlSpinner = new Spinner<>();
        ttlSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 86400, 60, 5));
        styleSpinner(ttlSpinner);
        modeBox.valueProperty().addListener((obs, oldValue, newValue) -> ttlSpinner.setDisable("AUDIT".equalsIgnoreCase(newValue)));

        TextField messageField = new TextField();
        messageField.setPromptText("Type a secure message...");
        styleField(messageField);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendBtn = accentBtn("Send", 13);
        Label actionLabel = mk("", FontWeight.NORMAL, 11, tm());
        Label hiddenTextWarningLabel = mk("", FontWeight.BOLD, 11, danger());
        hiddenTextWarningLabel.setWrapText(true);
        hiddenTextWarningLabel.setVisible(false);
        hiddenTextWarningLabel.setManaged(false);
        messageField.textProperty().addListener((obs, oldValue, newValue) -> {
            TextSafetyInspector.InspectionResult inspection = TextSafetyInspector.inspect(newValue);
            hiddenTextWarningLabel.setText(inspection.flagged() ? "Hidden-text flag: " + inspection.summary() : "");
            hiddenTextWarningLabel.setVisible(inspection.flagged());
            hiddenTextWarningLabel.setManaged(inspection.flagged());
        });

        Runnable sendAction = () -> {
            String message = messageField.getText().trim();
            if (message.isEmpty()) {
                actionLabel.setText("Enter a message first.");
                return;
            }
            TextSafetyInspector.InspectionResult inspection = TextSafetyInspector.inspect(message);
            String mode = modeBox.getValue();
            Integer ttl = "AUDIT".equalsIgnoreCase(mode) ? null : ttlSpinner.getValue();

            runTask(() -> sendSecureMessage(contactName, message, mode, ttl), responseObj -> {
                SendMessageResponse response = (SendMessageResponse) responseObj;
                addOutgoingMessage(contactName, message, mode, ttl, response.getThreatAnalysis(), inspection);
                renderConversation(messagesBox, contactName);
                messageField.clear();
                hiddenTextWarningLabel.setText("");
                hiddenTextWarningLabel.setVisible(false);
                hiddenTextWarningLabel.setManaged(false);
                scrollPane.setVvalue(1.0);
                actionLabel.setText(renderThreatStatus(response)
                        + (inspection.flagged() ? " | Hidden-text flag: " + inspection.summary() : ""));
                maybeShowThreatPopup(contactName, response.getThreatAnalysis());
                if (inspection.flagged()) {
                    showWarningPopup("Hidden Text Flag", "This outgoing message triggered the text safety checker:\n" + inspection.summary());
                }
            }, error -> actionLabel.setText(error.getMessage()));
        };

        sendBtn.setOnAction(event -> sendAction.run());
        messageField.setOnAction(event -> sendAction.run());

        HBox controls = new HBox(10,
                mk("Mode", FontWeight.BOLD, 11, tm()),
                modeBox,
                mk("TTL", FontWeight.BOLD, 11, tm()),
                ttlSpinner
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        HBox inputBar = new HBox(10, messageField, sendBtn);
        inputBar.setAlignment(Pos.CENTER);

        VBox composer = new VBox(10, controls, hiddenTextWarningLabel, inputBar, actionLabel);
        composer.setPadding(new Insets(14, 16, 14, 16));
        composer.setStyle("-fx-background-color:" + surf() + ";"
                + "-fx-border-color:" + bord() + ";-fx-border-width:1 0 0 0;");

        chatArea.getChildren().addAll(header, scrollPane, composer);
        return chatArea;
    }

    private VBox buildProfilePanel() {
        VBox pane = inlinePanel("User Profile");
        pane.getChildren().addAll(
                rowCenter(avatar(session.username(), 80, 32)),
                centered(mk(session.username(), FontWeight.BOLD, 20, t1())),
                centered(mk("Organization: " + safe(session.organization(), "default-org"), FontWeight.NORMAL, 13, t2())),
                centered(mk("Role: " + safe(session.role(), "USER"), FontWeight.NORMAL, 13, online())),
                centered(mk("Audit mode: " + (session.auditModeApproved() ? "approved" : "not approved"), FontWeight.NORMAL, 13, session.auditModeApproved() ? online() : danger())),
                sep(),
                secTitle("KEY STORAGE"),
                mk(keyManager.getBaseDirectory().resolve(session.username()).toString(), FontWeight.NORMAL, 12, tm())
        );
        return pane;
    }

    private VBox buildEncryptionPanel() {
        VBox pane = inlinePanel("Encryption Status");
        pane.getChildren().addAll(
                encCard("AES-256", "Message encryption", true),
                encCard("RSA-2048", "Key exchange and signatures", true),
                encCard("SHA-256", "Payload integrity", true),
                encCard("TTL", "Private message expiry", true),
                encCard("Audit Retention", "7-day governed retention", true),
                sep(),
                secTitle("THREAT MONITOR"),
                mk("Threat detection is active in the relay pipeline.", FontWeight.NORMAL, 12, t2())
        );
        return pane;
    }

    private VBox buildGovernancePanel() {
        VBox pane = inlinePanel("Governance");
        pane.getChildren().addAll(
                secTitle("AUDIT MODE"),
                mk("Audit mode is " + (session.auditModeApproved() ? "approved" : "not approved") + " for this user.", FontWeight.NORMAL, 12, t2())
        );

        Button requestExport = accentBtn("Request Audit Export", 12);
        requestExport.setOnAction(event -> requestAuditExport());

        Button myExports = secondaryBtn("View My Export Requests", 12);
        myExports.setOnAction(event -> showMyExports());

        pane.getChildren().addAll(requestExport, myExports);

        if ("ADMIN".equalsIgnoreCase(safe(session.role(), "USER"))) {
            pane.getChildren().addAll(sep(), secTitle("ADMIN TOOLS"));

            Button auditAccess = secondaryBtn("Approve or Revoke Audit Access", 12);
            auditAccess.setOnAction(event -> approveAuditAccess());
            Button exportApprovals = secondaryBtn("Review Export Requests", 12);
            exportApprovals.setOnAction(event -> reviewExportRequests());
            pane.getChildren().addAll(auditAccess, exportApprovals);
        }

        return pane;
    }

    private VBox buildSettingsPanel() {
        VBox pane = inlinePanel("Settings");
        Button theme = themeToggleBtn();
        theme.setMaxWidth(Double.MAX_VALUE);
        pane.getChildren().addAll(
                secTitle("APPEARANCE"),
                theme,
                sep(),
                secTitle("SESSION"),
                setRow("Server", session.serverUrl()),
                setRow("Username", session.username()),
                setRow("Organization", safe(session.organization(), "default-org")),
                setRow("Role", safe(session.role(), "USER"))
        );
        return pane;
    }

    private void requestAuditExport() {
        TextInputDialogBuilder.show("Export lookback days (1-7)", "7").ifPresent(value -> {
            int days = Integer.parseInt(value);
            runTask(() -> authClient.createExportRequest(days),
                    responseObj -> {
                        AuditExportResponse response = (AuditExportResponse) responseObj;
                        setTopStatus("Export request " + response.getRequestId() + " is " + response.getStatus());
                    },
                    error -> errorDialog("Export Request Failed", error.getMessage()));
        });
    }

    private void showMyExports() {
        runTask(() -> authClient.myExportRequests(), responsesObj -> {
            List<AuditExportResponse> responses = castExportList(responsesObj);
            if (responses.isEmpty()) {
                infoDialog("My Exports", "No export requests found.");
                return;
            }

            StringBuilder builder = new StringBuilder();
            for (AuditExportResponse response : responses) {
                builder.append(response.getRequestId())
                        .append(" | ")
                        .append(response.getStatus())
                        .append(" | ")
                        .append(response.getDetail())
                        .append(" | expires ")
                        .append(response.getExpiresAt())
                        .append("\n");
            }

            Optional<String> chosen = TextInputDialogBuilder.show("My export requests:\n" + builder + "\nEnter an APPROVED request id to export, or Cancel.", "");
            chosen.ifPresent(value -> exportAuditBundle(Long.parseLong(value)));
        }, error -> errorDialog("Load Exports Failed", error.getMessage()));
    }

    private void exportAuditBundle(long requestId) {
        runTask(() -> {
            Map<String, Object> export = authClient.exportAuditData(requestId);
            Path exportDirectory = Path.of(System.getProperty("user.home"), ".zerotrace", "exports");
            Files.createDirectories(exportDirectory);
            Path outputFile = exportDirectory.resolve("audit-export-" + requestId + ".json");
            authClient.writeJson(outputFile, export);
            return outputFile;
        }, output -> infoDialog("Export Saved", "Saved to:\n" + output), error -> errorDialog("Export Failed", error.getMessage()));
    }

    private void approveAuditAccess() {
        Optional<String> target = TextInputDialogBuilder.show("Username to review for audit access", "");
        if (target.isEmpty()) {
            return;
        }

        ButtonType grant = new ButtonType("Grant");
        ButtonType revoke = new ButtonType("Revoke");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Choose the audit action for " + target.get(), grant, revoke, ButtonType.CANCEL);
        styleDialog(alert);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
            return;
        }

        boolean approved = choice.get() == grant;
        runTask(() -> {
            authClient.setAuditApproval(target.get(), approved);
            return approved;
        }, approvedObj -> setTopStatus(((Boolean) approvedObj ? "Approved " : "Revoked ") + "audit access for " + target.get()),
                error -> errorDialog("Audit Update Failed", error.getMessage()));
    }

    private void reviewExportRequests() {
        runTask(() -> authClient.pendingExportRequests(), responsesObj -> {
            List<AuditExportResponse> responses = castExportList(responsesObj);
            if (responses.isEmpty()) {
                infoDialog("Pending Export Requests", "No pending export requests.");
                return;
            }

            StringBuilder builder = new StringBuilder();
            for (AuditExportResponse response : responses) {
                builder.append(response.getRequestId())
                        .append(" | ")
                        .append(response.getDetail())
                        .append(" | expires ")
                        .append(response.getExpiresAt())
                        .append("\n");
            }

            Optional<String> target = TextInputDialogBuilder.show("Pending requests:\n" + builder + "\nEnter request id to review", "");
            if (target.isEmpty()) {
                return;
            }

            long requestId = Long.parseLong(target.get());
            ButtonType approve = new ButtonType("Approve");
            ButtonType reject = new ButtonType("Reject");
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Review request " + requestId, approve, reject, ButtonType.CANCEL);
            styleDialog(alert);
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                return;
            }

            boolean approved = choice.get() == approve;
            runTask(() -> {
                authClient.approveExportRequest(requestId, approved);
                return approved;
            }, approvedObj -> setTopStatus(((Boolean) approvedObj ? "Approved" : "Rejected") + " export request " + requestId),
                    error -> errorDialog("Export Approval Failed", error.getMessage()));
        }, error -> errorDialog("Load Requests Failed", error.getMessage()));
    }

    private SendMessageResponse sendSecureMessage(String contactName, String message, String mode, Integer ttlSeconds) throws Exception {
        KeyPair senderKeys = keyManager.loadOrCreate(session.username());
        PublicKey receiverPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(contactName));
        MessagePacket packet = EncryptionService.encryptForRelay(
                session.username(),
                contactName,
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
    }

    private void refreshInbox(boolean showSuccess) {
        if (session == null || authClient == null) {
            return;
        }

        runTask(() -> {
            KeyPair receiverKeys = keyManager.loadOrCreate(session.username());
            List<MessagePacket> newPackets = authClient.fetchInbox(session.username());
            List<ChatEntry> entries = decryptPackets(newPackets, receiverKeys);
            List<ChatEntry> historyEntries = loadHistoryEntries(receiverKeys);
            return new RefreshResult(entries, historyEntries);
        }, resultObj -> {
            RefreshResult result = (RefreshResult) resultObj;
            List<ChatEntry> entries = result.newEntries();
            List<ChatEntry> historyEntries = result.historyEntries();
            for (ChatEntry entry : historyEntries) {
                addIncomingMessage(
                        entry.sender(),
                        entry.message(),
                        entry.mode(),
                        entry.ttlSeconds(),
                        entry.createdAt(),
                        entry.deliveredAt()
                );
            }
            for (ChatEntry entry : entries) {
                addIncomingMessage(
                        entry.sender(),
                        entry.message(),
                        entry.mode(),
                        entry.ttlSeconds(),
                        entry.createdAt(),
                        entry.deliveredAt()
                );
            }
            List<ChatEntry> allEntries = new ArrayList<>(historyEntries);
            allEntries.addAll(entries);
            maybeShowInboxSecurityPopups(allEntries);
            refreshPeoplePanel();
            if (VIEW_CHAT.equals(activeView)
                    && selectedContact != null
                    && selectedContact.equals(activeChatViewContact)
                    && activeMessagesBox != null) {
                renderConversation(activeMessagesBox, selectedContact);
            } else if (VIEW_OPEN_CHATS.equals(activeView)) {
                setContent(buildOpenChatsContent());
            }
            if (showSuccess) {
                setTopStatus(entries.isEmpty()
                        ? (historyEntries.isEmpty() ? "Inbox checked. No visible messages." : "Inbox checked. History reloaded.")
                        : "Inbox refreshed.");
            }
        }, error -> errorDialog("Inbox Refresh Failed", error.getMessage()));
    }

    private List<ChatEntry> loadHistoryEntries(KeyPair receiverKeys) throws Exception {
        try {
            List<MessagePacket> historyPackets = authClient.fetchHistory(session.username());
            return decryptPackets(historyPackets, receiverKeys);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ChatEntry> decryptPackets(List<MessagePacket> packets, KeyPair receiverKeys) throws Exception {
        List<ChatEntry> entries = new ArrayList<>();
        for (MessagePacket packet : packets) {
            PublicKey senderPublicKey = RSAUtil.getPublicKeyFromString(authClient.getPublicKey(packet.getSender()));
            String plaintext;
            try {
                plaintext = EncryptionService.decryptFromRelay(packet, receiverKeys.getPrivate(), senderPublicKey);
            } catch (Exception securityException) {
                plaintext = "Rejected tampered message (" + securityException.getMessage() + ")";
            }
            TextSafetyInspector.InspectionResult inspection = TextSafetyInspector.inspect(plaintext);
            entries.add(new ChatEntry(
                    packet.getSender(),
                    plaintext,
                    packet.getMode(),
                    packet.getTtlSeconds(),
                    packet.getCreatedAt(),
                    packet.getDeliveredAt(),
                    false,
                    null,
                    null,
                    inspection.flagged(),
                    inspection.flagged() ? inspection.summary() : null
            ));
        }
        return entries;
    }

    private void addOutgoingMessage(
            String contact,
            String message,
            String mode,
            Integer ttlSeconds,
            ThreatAnalysisResult threatAnalysis,
            TextSafetyInspector.InspectionResult inspection
    ) {
        addContact(contact);
        chatHistory.computeIfAbsent(contact, key -> new ArrayList<>())
                .add(new ChatEntry(
                        session.username(),
                        message,
                        mode,
                        ttlSeconds,
                        Instant.now().toString(),
                        null,
                        true,
                        threatAnalysis == null ? null : threatAnalysis.getVerdict(),
                        threatAnalysis == null ? null : threatAnalysis.getDetail(),
                        inspection != null && inspection.flagged(),
                        inspection != null && inspection.flagged() ? inspection.summary() : null
                ));
        refreshPeoplePanel();
    }

    private void addIncomingMessage(
            String contact,
            String message,
            String mode,
            Integer ttlSeconds,
            String createdAt,
            String deliveredAt
    ) {
        addContact(contact);
        List<ChatEntry> history = chatHistory.computeIfAbsent(contact, key -> new ArrayList<>());
        boolean duplicate = history.stream().anyMatch(entry ->
                !entry.outgoing()
                        && entry.createdAt().equals(createdAt)
                        && entry.message().equals(message)
                        && entry.mode().equals(mode)
        );
        if (!duplicate) {
            TextSafetyInspector.InspectionResult inspection = TextSafetyInspector.inspect(message);
            history.add(new ChatEntry(
                    contact,
                    message,
                    mode,
                    ttlSeconds,
                    createdAt,
                    deliveredAt,
                    false,
                    null,
                    null,
                    inspection.flagged(),
                    inspection.flagged() ? inspection.summary() : null
            ));
        }
    }

    private void addContact(String contact) {
        chatHistory.computeIfAbsent(contact, key -> new ArrayList<>());
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(contact);
        ordered.addAll(contacts);
        contacts.setAll(ordered);
    }

    private ChatEntry latestMessage(String contact) {
        List<ChatEntry> history = chatHistory.get(contact);
        if (history == null || history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1);
    }

    private void refreshPeoplePanel() {
        if (mainRoot == null) {
            return;
        }
        HBox body = (HBox) mainRoot.getCenter();
        peoplePanel = buildPeopleList();
        body.getChildren().set(1, peoplePanel);
    }

    private void renderConversation(VBox messagesBox, String contactName) {
        pruneExpiredReceivedMessages();
        messagesBox.getChildren().clear();
        List<ChatEntry> history = new ArrayList<>(chatHistory.getOrDefault(contactName, List.of()));
        history.sort(Comparator.comparing(ChatEntry::createdAt));

        if (history.isEmpty()) {
            messagesBox.getChildren().add(mk("No messages yet. Send the first secure message.", FontWeight.NORMAL, 12, tm()));
            return;
        }

        for (ChatEntry entry : history) {
            if (entry.outgoing()) {
                messagesBox.getChildren().add(sentBubble(
                        entry.message(),
                        entry.mode(),
                        entry.ttlSeconds(),
                        entry.threatVerdict(),
                        entry.threatDetail(),
                        entry.embeddedTextFlagged(),
                        entry.embeddedTextDetail()
                ));
            } else {
                messagesBox.getChildren().add(recvBubble(
                        contactName,
                        entry.message(),
                        entry.mode(),
                        entry.ttlSeconds(),
                        entry.embeddedTextFlagged(),
                        entry.embeddedTextDetail()
                ));
            }
        }
    }

    private void setContent(VBox pane) {
        HBox body = (HBox) mainRoot.getCenter();
        contentPanel = pane;
        HBox.setHgrow(contentPanel, Priority.ALWAYS);
        body.getChildren().set(2, contentPanel);
        if (pane != activeChatContainer) {
            activeChatContainer = null;
            activeMessagesBox = null;
            activeChatViewContact = null;
        }
    }

    private void showOpenChatsView() {
        activeView = VIEW_OPEN_CHATS;
        setContent(buildOpenChatsContent());
    }

    private void showNonChatView(VBox pane) {
        activeView = VIEW_OTHER;
        setContent(pane);
    }

    private void logout() {
        stopTtlSweep();
        try {
            sessionManager.clear();
        } catch (Exception ignored) {
        }
        session = null;
        authClient = null;
        selectedContact = null;
        activeView = VIEW_OPEN_CHATS;
        activeChatContainer = null;
        activeMessagesBox = null;
        activeChatViewContact = null;
        contacts.clear();
        chatHistory.clear();
        showLoginPage();
    }

    private void startTtlSweep() {
        stopTtlSweep();
        ttlSweepTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (pruneExpiredReceivedMessages()) {
                refreshPeoplePanel();
                if (VIEW_CHAT.equals(activeView)
                        && selectedContact != null
                        && selectedContact.equals(activeChatViewContact)
                        && activeMessagesBox != null) {
                    renderConversation(activeMessagesBox, selectedContact);
                } else if (VIEW_OPEN_CHATS.equals(activeView)) {
                    setContent(buildOpenChatsContent());
                }
            }
        }));
        ttlSweepTimeline.setCycleCount(Animation.INDEFINITE);
        ttlSweepTimeline.play();
    }

    private void stopTtlSweep() {
        if (ttlSweepTimeline != null) {
            ttlSweepTimeline.stop();
            ttlSweepTimeline = null;
        }
    }

    private boolean pruneExpiredReceivedMessages() {
        boolean changed = false;
        Instant now = Instant.now();
        for (List<ChatEntry> history : chatHistory.values()) {
            changed |= history.removeIf(entry -> isExpiredReceivedPrivateMessage(entry, now));
        }
        return changed;
    }

    private boolean isExpiredReceivedPrivateMessage(ChatEntry entry, Instant now) {
        if (entry == null || entry.outgoing()) {
            return false;
        }
        if (!"PRIVATE".equalsIgnoreCase(entry.mode()) || entry.ttlSeconds() == null || entry.deliveredAt() == null) {
            return false;
        }
        try {
            Instant deliveredAt = Instant.parse(entry.deliveredAt());
            return !deliveredAt.plusSeconds(entry.ttlSeconds()).isAfter(now);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void runTask(TaskSupplier supplier, TaskSuccess success, TaskFailure failure) {
        Task<Object> task = new Task<>() {
            @Override
            protected Object call() throws Exception {
                return supplier.get();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            failure.accept(ex instanceof Exception exception ? exception : new Exception(ex));
        });
        Thread worker = new Thread(task, "zerotrace-ui-task");
        worker.setDaemon(true);
        worker.start();
    }

    private void setTopStatus(String text) {
        if (topStatusLabel != null) {
            topStatusLabel.setText(text);
        }
    }

    private VBox inlinePanel(String title) {
        VBox pane = new VBox(14);
        pane.setPadding(new Insets(30));
        pane.setStyle("-fx-background-color:" + bg() + ";");
        pane.getChildren().add(mk(title, FontWeight.BOLD, 22, t1()));
        return pane;
    }

    private HBox sentBubble(
            String message,
            String mode,
            Integer ttlSeconds,
            String threatVerdict,
            String threatDetail,
            boolean embeddedTextFlagged,
            String embeddedTextDetail
    ) {
        VBox content = new VBox(3);
        content.setAlignment(Pos.CENTER_RIGHT);
        if (shouldShowThreatAlert(threatVerdict)) {
            content.getChildren().add(threatAlert(threatVerdict, threatDetail));
        }
        if (embeddedTextFlagged) {
            content.getChildren().add(embeddedTextAlert(embeddedTextDetail));
        }
        content.getChildren().addAll(
            mk(modeLabel(mode, ttlSeconds), FontWeight.BOLD, 10, "#ffffff"),
            bubbleLabel(message, bsent(), "#ffffff", 12, 12, 2, 12)
        );
        HBox box = new HBox(content);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private HBox recvBubble(
            String sender,
            String message,
            String mode,
            Integer ttlSeconds,
            boolean embeddedTextFlagged,
            String embeddedTextDetail
    ) {
        VBox content = new VBox(3);
        if (embeddedTextFlagged) {
            content.getChildren().add(embeddedTextAlert(embeddedTextDetail));
        }
        content.getChildren().addAll(
                mk(sender + "  |  " + modeLabel(mode, ttlSeconds), FontWeight.BOLD, 10, adim()),
                bubbleLabel(message, brecv(), t1(), 12, 12, 12, 2)
        );
        HBox box = new HBox(content);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Label bubbleLabel(String message, String background, String textColor, int tl, int tr, int br, int bl) {
        Label label = new Label(message);
        label.setWrapText(true);
        label.setMaxWidth(380);
        label.setFont(Font.font("Monospace", 13));
        label.setTextFill(Color.web(textColor));
        label.setPadding(new Insets(10, 14, 10, 14));
        label.setStyle("-fx-background-color:" + background + ";"
                + "-fx-background-radius:" + tl + " " + tr + " " + br + " " + bl + ";");
        return label;
    }

    private VBox encCard(String protocol, String purpose, boolean active) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle("-fx-background-color:" + surf2() + ";-fx-background-radius:6;"
                + "-fx-border-color:" + (active ? asoft() : danger() + "44") + ";"
                + "-fx-border-radius:6;-fx-border-width:1;");
        HBox row = new HBox();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(mk(protocol, FontWeight.BOLD, 12, t1()), spacer, mk(active ? "ON" : "OFF", FontWeight.BOLD, 11, active ? online() : danger()));
        card.getChildren().addAll(row, mk(purpose, FontWeight.NORMAL, 10, tm()));
        return card;
    }

    private StackPane avatar(String name, double size, int fontSize) {
        StackPane avatar = new StackPane();
        avatar.setPrefSize(size, size);
        avatar.setMinSize(size, size);
        avatar.setStyle("-fx-background-color:" + asoft() + ";-fx-background-radius:" + (size / 2) + ";"
                + "-fx-border-color:" + adim() + ";-fx-border-radius:" + (size / 2) + ";-fx-border-width:1;");
        avatar.getChildren().add(mk(String.valueOf(Character.toUpperCase(name.charAt(0))), FontWeight.BOLD, fontSize, acc()));
        return avatar;
    }

    private HBox setRow(String label, String value) {
        HBox row = new HBox();
        row.setPadding(new Insets(5, 0, 5, 0));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(mk(label, FontWeight.NORMAL, 13, t2()), spacer, mk(value, FontWeight.BOLD, 13, t1()));
        return row;
    }

    private Label secTitle(String text) {
        Label label = mk(text, FontWeight.BOLD, 11, tm());
        label.setPadding(new Insets(6, 0, 2, 0));
        return label;
    }

    private VBox column(Label... labels) {
        VBox box = new VBox(2);
        box.getChildren().addAll(labels);
        return box;
    }

    private HBox rowCenter(javafx.scene.Node node) {
        HBox box = new HBox(node);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private Label centered(Label label) {
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private HBox rowRight(Button button) {
        HBox row = new HBox(button);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private Label footer() {
        Label footer = mk("End-to-End Encrypted  |  RSA + AES  |  SHA-256", FontWeight.NORMAL, 11, tm());
        VBox.setMargin(footer, new Insets(28, 0, 0, 0));
        return footer;
    }

    private Label mk(String text, FontWeight weight, int size, String color) {
        Label label = new Label(text);
        label.setFont(Font.font("Monospace", weight, size));
        label.setTextFill(Color.web(color));
        return label;
    }

    private Separator sep() {
        Separator separator = new Separator();
        separator.setStyle("-fx-background-color:" + bord() + ";");
        return separator;
    }

    private Label fldLbl(String text) {
        return mk(text, FontWeight.NORMAL, 11, tm());
    }

    private Button accentBtn(String text, int size) {
        Button button = new Button(text);
        button.setFont(Font.font("Monospace", FontWeight.BOLD, size));
        button.setStyle("-fx-background-color:" + acc() + ";-fx-text-fill:#ffffff;"
                + "-fx-background-radius:6;-fx-cursor:hand;-fx-padding:10 18;");
        return button;
    }

    private Button secondaryBtn(String text, int size) {
        Button button = new Button(text);
        button.setFont(Font.font("Monospace", FontWeight.BOLD, size));
        button.setStyle("-fx-background-color:" + asoft() + ";-fx-text-fill:" + acc() + ";"
                + "-fx-border-color:" + adim() + ";-fx-border-radius:6;-fx-background-radius:6;"
                + "-fx-cursor:hand;-fx-padding:10 18;");
        return button;
    }

    private Button themeToggleBtn() {
        Button button = new Button(isDarkTheme ? "Light Mode" : "Dark Mode");
        button.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        button.setStyle("-fx-background-color:" + asoft() + ";-fx-text-fill:" + acc() + ";"
                + "-fx-border-color:" + adim() + ";-fx-border-radius:20;-fx-background-radius:20;"
                + "-fx-cursor:hand;-fx-padding:6 14;");
        button.setOnAction(event -> {
            isDarkTheme = !isDarkTheme;
            if (session == null) {
                showLoginPage();
            } else {
                showHomePage();
                if (selectedContact != null) {
                    setContent(buildChatArea(selectedContact));
                }
            }
        });
        return button;
    }

    private void styleField(TextField field) {
        field.setFont(Font.font("Monospace", 13));
        field.setStyle("-fx-background-color:" + surf2() + ";-fx-text-fill:" + t1() + ";"
                + "-fx-prompt-text-fill:" + tm() + ";-fx-border-color:" + bord() + ";"
                + "-fx-border-radius:6;-fx-background-radius:6;-fx-padding:10;");
    }

    private void stylePassField(PasswordField field) {
        field.setFont(Font.font("Monospace", 13));
        field.setStyle("-fx-background-color:" + surf2() + ";-fx-text-fill:" + t1() + ";"
                + "-fx-prompt-text-fill:" + tm() + ";-fx-border-color:" + bord() + ";"
                + "-fx-border-radius:6;-fx-background-radius:6;-fx-padding:10;");
    }

    private void styleCombo(ComboBox<String> comboBox) {
        comboBox.setStyle("-fx-background-color:" + surf2() + ";-fx-text-fill:" + t1() + ";"
                + "-fx-border-color:" + bord() + ";-fx-background-radius:6;-fx-border-radius:6;");
    }

    private void styleSpinner(Spinner<Integer> spinner) {
        spinner.setEditable(true);
        spinner.setStyle("-fx-background-color:" + surf2() + ";-fx-text-fill:" + t1() + ";"
                + "-fx-border-color:" + bord() + ";-fx-background-radius:6;-fx-border-radius:6;");
    }

    private void infoDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void showWarningPopup(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void showErrorPopup(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void errorDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void styleDialog(Alert alert) {
        Platform.runLater(() -> {
            if (alert.getDialogPane().getScene() != null) {
                alert.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });
    }

    private void promptForContact() {
        TextInputDialogBuilder.show("Enter the username you want to message", "").ifPresent(this::showChatPage);
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

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String modeLabel(String mode, Integer ttlSeconds) {
        if ("AUDIT".equalsIgnoreCase(mode)) {
            return "AUDIT";
        }
        return ttlSeconds == null ? "PRIVATE" : "PRIVATE | TTL " + ttlSeconds + "s";
    }

    private boolean shouldShowThreatAlert(String threatVerdict) {
        return "ANOMALY".equalsIgnoreCase(threatVerdict) || "SUSPICIOUS".equalsIgnoreCase(threatVerdict);
    }

    private void maybeShowThreatPopup(String contactName, ThreatAnalysisResult threatAnalysis) {
        if (threatAnalysis == null || !shouldShowThreatAlert(threatAnalysis.getVerdict())) {
            return;
        }

        String detail = safe(threatAnalysis.getDetail(), "Threat monitor flagged this message.");
        if ("ANOMALY".equalsIgnoreCase(threatAnalysis.getVerdict())) {
            showErrorPopup(
                    "Anomaly Detected",
                    "Traffic to " + contactName + " was flagged by the AI monitor.\n\n"
                            + threatAnalysis.getVerdict() + ": " + detail
            );
            return;
        }

        showWarningPopup(
                "Suspicious Activity",
                "Traffic to " + contactName + " raised a threat warning.\n\n"
                        + threatAnalysis.getVerdict() + ": " + detail
        );
    }

    private void maybeShowInboxSecurityPopups(List<ChatEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        long tamperedCount = entries.stream()
                .filter(entry -> entry.message() != null && entry.message().startsWith("Rejected tampered message"))
                .count();
        if (tamperedCount > 0) {
            showErrorPopup(
                    "Tampered Message Rejected",
                    tamperedCount + " incoming message(s) failed integrity verification and were blocked."
            );
        }

        List<ChatEntry> hiddenTextFlags = entries.stream()
                .filter(ChatEntry::embeddedTextFlagged)
                .toList();
        if (!hiddenTextFlags.isEmpty()) {
            ChatEntry first = hiddenTextFlags.get(0);
            showWarningPopup(
                    "Hidden Text Flag",
                    hiddenTextFlags.size() + " incoming message(s) triggered the text safety checker.\n\n"
                            + safe(first.embeddedTextDetail(), "Embedded or invisible text pattern detected.")
            );
        }
    }

    private Label threatAlert(String threatVerdict, String threatDetail) {
        String body = safe(threatDetail, "Threat monitor flagged this message.");
        Label label = new Label(threatVerdict.toUpperCase(Locale.ROOT) + " DETECTED  |  " + body);
        label.setWrapText(true);
        label.setMaxWidth(460);
        label.setFont(Font.font("Monospace", FontWeight.EXTRA_BOLD, 12));
        label.setTextFill(Color.web("#ffffff"));
        label.setPadding(new Insets(9, 14, 9, 14));
        label.setStyle("-fx-background-color:#ff1744;"
                + "-fx-background-radius:10;"
                + "-fx-border-color:#ffffff;"
                + "-fx-border-radius:10;-fx-border-width:2;");

        FadeTransition flash = new FadeTransition(Duration.millis(650), label);
        flash.setFromValue(1.0);
        flash.setToValue(0.35);
        flash.setAutoReverse(true);
        flash.setCycleCount(Animation.INDEFINITE);
        flash.play();
        return label;
    }

    private Label embeddedTextAlert(String detail) {
        Label label = new Label("HIDDEN TEXT FLAG  |  " + safe(detail, "Embedded or invisible text pattern detected."));
        label.setWrapText(true);
        label.setMaxWidth(420);
        label.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#1a1f2e"));
        label.setPadding(new Insets(7, 12, 7, 12));
        label.setStyle("-fx-background-color:#ffd666;"
                + "-fx-background-radius:10;"
                + "-fx-border-color:#ffec99;"
                + "-fx-border-radius:10;-fx-border-width:1.5;");
        return label;
    }

    @SuppressWarnings("unchecked")
    private List<AuditExportResponse> castExportList(Object value) {
        return (List<AuditExportResponse>) value;
    }

    private record RefreshResult(List<ChatEntry> newEntries, List<ChatEntry> historyEntries) {
    }

    private record ChatEntry(
            String sender,
            String message,
            String mode,
            Integer ttlSeconds,
            String createdAt,
            String deliveredAt,
            boolean outgoing,
            String threatVerdict,
            String threatDetail,
            boolean embeddedTextFlagged,
            String embeddedTextDetail
    ) {
    }

    private record LoginContext(SessionManager.Session session, AuthClient authClient) {
    }

    @FunctionalInterface
    private interface TaskSupplier {
        Object get() throws Exception;
    }

    @FunctionalInterface
    private interface TaskSuccess {
        void accept(Object value);
    }

    @FunctionalInterface
    private interface TaskFailure {
        void accept(Exception exception);
    }

    private static final class TextInputDialogBuilder {
        private static Optional<String> show(String contentText, String defaultValue) {
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(defaultValue);
            dialog.setTitle("ZeroTrace");
            dialog.setHeaderText(null);
            dialog.setContentText(contentText);
            Optional<String> result = dialog.showAndWait();
            return result.map(String::trim).filter(value -> !value.isEmpty());
        }
    }
}
