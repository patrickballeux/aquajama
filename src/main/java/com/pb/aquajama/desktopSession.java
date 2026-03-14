/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JInternalFrame.java to edit this template
 */
package com.pb.aquajama;

import com.pb.aquajama.ollama.Token;
import com.pb.aquajama.sessions.Session;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 *
 * @author patrickballeux
 */
public class desktopSession extends javax.swing.JInternalFrame {

    private final Session session;

    private final StyledDocument responseDoc;
    private final Style normalStyle;
    private final Style boldStyle;
    private final Style italicStyle;
    private final Style codeStyle;
    private final Style headingStyle;
    private final Style listStyle;
    private final Style boldItalic;

    private final StringBuilder streamBuffer = new StringBuilder();

    /**
     * Creates new form desktopSession
     *
     * @param session
     */
    public desktopSession(Session session) {
        initComponents();
        responseDoc = txtResponse.getStyledDocument();

        normalStyle = txtResponse.addStyle("normal", null);

        boldStyle = txtResponse.addStyle("bold", null);
        StyleConstants.setBold(boldStyle, true);

        italicStyle = txtResponse.addStyle("italic", null);
        StyleConstants.setItalic(italicStyle, true);

        codeStyle = txtResponse.addStyle("code", null);
        StyleConstants.setFontFamily(codeStyle, "Monospaced");
        StyleConstants.setBackground(codeStyle, new java.awt.Color(240, 240, 240));

        headingStyle = txtResponse.addStyle("heading", null);
        StyleConstants.setBold(headingStyle, true);
        StyleConstants.setFontSize(headingStyle, 18);

        listStyle = txtResponse.addStyle("list", null);
        StyleConstants.setLeftIndent(listStyle, 10);

        boldItalic = txtResponse.addStyle("boldItalic", null);
        StyleConstants.setBold(boldItalic, true);
        StyleConstants.setItalic(boldItalic, true);

        InputMap im = txtPrompt.getInputMap();
        ActionMap am = txtPrompt.getActionMap();

// ENTER → send message
        im.put(KeyStroke.getKeyStroke("ENTER"), "sendPrompt");

// SHIFT+ENTER → newline
        im.put(KeyStroke.getKeyStroke("shift ENTER"), "insertBreak");

        am.put("sendPrompt", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                btnSend.doClick();
            }
        });

        am.put("insertBreak", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                txtPrompt.append("\n");
            }
        });
        this.session = session;
        this.setTitle("MODEL: %s".formatted(session.getModel().name()));
        session.setUiConsumer(token -> {
            javax.swing.SwingUtilities.invokeLater(() -> {
                // Basic append
                appendToken(token);
            });
        });

        lblModelName.setText(session.getModel().name());
        chkVision.setSelected(session.getModel().canUseVision());
        chkThink.setSelected(session.getModel().canThink());
        chkVision.setEnabled(false);
        chkThink.setEnabled(false);

        javax.swing.SwingUtilities.invokeLater(() -> txtPrompt.requestFocusInWindow());
        javax.swing.SwingUtilities.invokeLater(() -> this.pack());
    }

    private void appendToken(Token token) {

        if (token.isThinking()) {
            String text = txtThinking.getText();
            text = text.concat(token.text());
            if (text.contains("\n")) {
                text = text.substring(text.indexOf("\n")).trim();
            }
            if (text.length() > 150) {
                text = text.substring(50);
            }
            txtThinking.setText(text);
            return;
        }

        txtThinking.setText("");

        streamBuffer.append(token.text());
        boolean fromUser = token.fromUser();
        while (true) {

            int newline = streamBuffer.indexOf("\n");

            if (newline == -1) {
                break;
            }

            String line = streamBuffer.substring(0, newline);
            streamBuffer.delete(0, newline + 1);

            renderMarkdownLine(line, fromUser);
        }
        if (streamBuffer.length() > 0 && streamBuffer.length() > 120) {
            renderMarkdownLine(streamBuffer.toString(), fromUser);
            streamBuffer.setLength(0);
        }
    }

    private void renderMarkdownLine(String line, boolean fromUser) {

        try {

            // headings
            if (line.startsWith("# ")) {
                insertStyled(line.substring(2) + "\n", headingStyle, fromUser);
                return;
            }

            // bullet list
            if (line.startsWith("- ")) {
                insertStyled("• " + line.substring(2) + "\n", listStyle, fromUser);
                return;
            }

            int i = 0;

            while (i < line.length()) {

                // bold
                if (line.startsWith("**", i)) {
                    int end = line.indexOf("**", i + 2);
                    if (end != -1) {
                        insertStyled(line.substring(i + 2, end), boldStyle, fromUser);
                        i = end + 2;
                        continue;
                    }
                }

                // italic
                if (line.startsWith("*", i)) {
                    int end = line.indexOf("*", i + 1);
                    if (end != -1) {
                        insertStyled(line.substring(i + 1, end), italicStyle, fromUser);
                        i = end + 1;
                        continue;
                    }
                }

                // inline code
                if (line.startsWith("`", i)) {
                    int end = line.indexOf("`", i + 1);
                    if (end != -1) {
                        insertStyled(line.substring(i + 1, end), codeStyle, fromUser);
                        i = end + 1;
                        continue;
                    }
                }

                insertStyled(String.valueOf(line.charAt(i)), normalStyle, fromUser);
                i++;
            }

            insertStyled("\n", normalStyle, fromUser);

            txtResponse.setCaretPosition(responseDoc.getLength());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertStyled(String text, Style style, boolean fromUser) throws Exception {
        if (!text.isEmpty()) {
            if (fromUser) {
                StyleConstants.setForeground(style, java.awt.Color.GRAY);
            } else {
                StyleConstants.setForeground(style, java.awt.Color.BLACK);
            }

            responseDoc.insertString(responseDoc.getLength(), text, style);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panBottom = new javax.swing.JPanel();
        btnSend = new javax.swing.JButton();
        scrollPrompt = new javax.swing.JScrollPane();
        txtPrompt = new javax.swing.JTextArea();
        txtThinking = new javax.swing.JLabel();
        panTop = new javax.swing.JPanel();
        lblModel = new javax.swing.JLabel();
        lblModelName = new javax.swing.JLabel();
        chkVision = new javax.swing.JCheckBox();
        chkThink = new javax.swing.JCheckBox();
        scrollMD = new javax.swing.JScrollPane();
        txtResponse = new javax.swing.JTextPane();

        setClosable(true);
        setIconifiable(true);
        setMaximizable(true);
        setResizable(true);
        setTitle("Session");
        setSize(new java.awt.Dimension(456, 227));
        setVisible(true);

        btnSend.setLabel("Send");
        btnSend.addActionListener(this::btnSendActionPerformed);

        txtPrompt.setColumns(20);
        txtPrompt.setLineWrap(true);
        txtPrompt.setRows(5);
        txtPrompt.setWrapStyleWord(true);
        scrollPrompt.setViewportView(txtPrompt);

        txtThinking.setForeground(new java.awt.Color(153, 153, 153));
        txtThinking.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        txtThinking.setText(" ");
        txtThinking.setMinimumSize(new java.awt.Dimension(100, 17));

        javax.swing.GroupLayout panBottomLayout = new javax.swing.GroupLayout(panBottom);
        panBottom.setLayout(panBottomLayout);
        panBottomLayout.setHorizontalGroup(
            panBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panBottomLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(txtThinking, javax.swing.GroupLayout.DEFAULT_SIZE, 322, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(btnSend)
                .addContainerGap())
            .addGroup(panBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panBottomLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(scrollPrompt, javax.swing.GroupLayout.DEFAULT_SIZE, 406, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        panBottomLayout.setVerticalGroup(
            panBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panBottomLayout.createSequentialGroup()
                .addContainerGap(100, Short.MAX_VALUE)
                .addGroup(panBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSend)
                    .addComponent(txtThinking, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
            .addGroup(panBottomLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panBottomLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(scrollPrompt, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(32, Short.MAX_VALUE)))
        );

        getContentPane().add(panBottom, java.awt.BorderLayout.SOUTH);

        lblModel.setText("Model");

        lblModelName.setText("Model Name");

        chkVision.setText("Vision");

        chkThink.setText("Think");

        javax.swing.GroupLayout panTopLayout = new javax.swing.GroupLayout(panTop);
        panTop.setLayout(panTopLayout);
        panTopLayout.setHorizontalGroup(
            panTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panTopLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblModel, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblModelName, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 48, Short.MAX_VALUE)
                .addComponent(chkVision)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(chkThink)
                .addContainerGap())
        );
        panTopLayout.setVerticalGroup(
            panTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panTopLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(panTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblModelName)
                    .addComponent(lblModel)
                    .addComponent(chkVision)
                    .addComponent(chkThink))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        getContentPane().add(panTop, java.awt.BorderLayout.PAGE_START);

        txtResponse.setEditable(false);
        txtResponse.setBackground(new java.awt.Color(255, 255, 255));
        txtResponse.setMinimumSize(new java.awt.Dimension(62, 200));
        scrollMD.setViewportView(txtResponse);

        getContentPane().add(scrollMD, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSendActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendActionPerformed
        streamBuffer.setLength(0);
        session.sendUserPrompt(txtPrompt.getText());
        txtPrompt.setText("");
    }//GEN-LAST:event_btnSendActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnSend;
    private javax.swing.JCheckBox chkThink;
    private javax.swing.JCheckBox chkVision;
    private javax.swing.JLabel lblModel;
    private javax.swing.JLabel lblModelName;
    private javax.swing.JPanel panBottom;
    private javax.swing.JPanel panTop;
    private javax.swing.JScrollPane scrollMD;
    private javax.swing.JScrollPane scrollPrompt;
    private javax.swing.JTextArea txtPrompt;
    private javax.swing.JTextPane txtResponse;
    private javax.swing.JLabel txtThinking;
    // End of variables declaration//GEN-END:variables
}
