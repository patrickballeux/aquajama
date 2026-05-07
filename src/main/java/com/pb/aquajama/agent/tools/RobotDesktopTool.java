package com.pb.aquajama.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.pb.aquajama.sessions.Session;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;

public class RobotDesktopTool implements AgentTool {

    private final Robot robot;

    public RobotDesktopTool() {
        try {
            this.robot = new Robot();
            robot.setAutoDelay(50);
        } catch (AWTException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String getActionName() {
        return "robot_desktop";
    }

    @Override
    public String buildRuleSnippet() {
        return """
Tool: robot_desktop

Control the mouse, keyboard and screen.

Commands:
move_mouse
click
double_click
type_text
press_key
scroll
screenshot
wait

Return only JSON when using this tool.
""";
    }

    @Override
    public boolean supports(JsonNode action) {
        return "robot_desktop".equals(action.path("action").asText());
    }

    @Override
    public void execute(JsonNode node, Session session) throws Exception {

        String command = node.path("command").asText();

        switch (command) {

            case "move_mouse" -> {
                int x = node.get("x").asInt();
                int y = node.get("y").asInt();
                robot.mouseMove(x, y);
            }

            case "click" -> {
                int x = node.get("x").asInt();
                int y = node.get("y").asInt();

                robot.mouseMove(x, y);
                robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
            }

            case "double_click" -> {
                int x = node.get("x").asInt();
                int y = node.get("y").asInt();

                robot.mouseMove(x, y);

                for (int i = 0; i < 2; i++) {
                    robot.mousePress(KeyEvent.BUTTON1_DOWN_MASK);
                    robot.mouseRelease(KeyEvent.BUTTON1_DOWN_MASK);
                }
            }

            case "type_text" -> {

                String text = node.get("text").asText();

                for (char c : text.toCharArray()) {
                    int key = KeyEvent.getExtendedKeyCodeForChar(c);

                    if (KeyEvent.CHAR_UNDEFINED == key) {
                        continue;
                    }

                    robot.keyPress(key);
                    robot.keyRelease(key);
                }
            }

            case "press_key" -> {

                for (JsonNode k : node.withArray("keys")) {

                    int key = keyCode(k.asText());
                    robot.keyPress(key);
                }

                for (JsonNode k : node.withArray("keys")) {

                    int key = keyCode(k.asText());
                    robot.keyRelease(key);
                }
            }

            case "scroll" -> {

                int amount = node.get("amount").asInt();
                robot.mouseWheel(amount);
            }

            case "wait" -> {

                int ms = node.path("ms").asInt(1000);
                Thread.sleep(ms);
            }

            case "screenshot" -> {

                Rectangle screen
                        = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                BufferedImage image = robot.createScreenCapture(screen);

                session.sendToolResult(
                        "Screenshot captured.  Answer the prompt of the user from this image.",
                        List.of(image)
                );

                return;
            }
        }

        session.sendToolResult("Command executed: " + command, List.of());
    }

    private int keyCode(String key) {

        return switch (key) {
            case "ENTER" ->
                KeyEvent.VK_ENTER;
            case "META" ->
                KeyEvent.VK_META;
            case "CMD" ->
                KeyEvent.VK_META;
            case "CTRL" ->
                KeyEvent.VK_CONTROL;
            case "SHIFT" ->
                KeyEvent.VK_SHIFT;
            case "ALT" ->
                KeyEvent.VK_ALT;
            case "R" ->
                KeyEvent.VK_R;
            case "C" ->
                KeyEvent.VK_C;
            case "V" ->
                KeyEvent.VK_V;
            default ->
                KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
        };
    }
}
