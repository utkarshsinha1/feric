package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TerminalWorkspaceTest {
    @Test
    void closeTabButtonRemovesItsTerminalSession() throws Exception {
        TerminalWorkspace[] workspace = new TerminalWorkspace[1];
        JButton[] closeButton = new JButton[1];
        SwingUtilities.invokeAndWait(() -> {
            workspace[0] = new TerminalWorkspace(() -> Path.of(System.getProperty("user.home")));
            JTabbedPane tabs = findComponent(workspace[0], JTabbedPane.class);
            assertNotNull(tabs);
            Container tabHeader = (Container) tabs.getTabComponentAt(0);
            closeButton[0] = findButton(tabHeader);
            assertNotNull(closeButton[0]);
            closeButton[0].doClick();
        });

        assertNull(workspace[0].activeTerminal());
    }

    private static JButton findButton(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) {
                return button;
            }
            if (component instanceof Container child) {
                JButton found = findButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T findComponent(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container child) {
                T found = findComponent(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
