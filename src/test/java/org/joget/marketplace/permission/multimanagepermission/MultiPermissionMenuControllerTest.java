package org.joget.marketplace.permission.multimanagepermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiPermissionMenuControllerTest {

    @Test
    public void anyModeAuthorizesWhenOneKeyMatches() {
        TestController controller = controller("any", "view", "edit");
        controller.authorizedKeys.add("edit");

        assertTrue(controller.isAuthorize());
        assertEquals(List.of("view", "edit"), controller.checkedKeys);
    }

    @Test
    public void anyModeStopsAfterFirstMatchingKey() {
        TestController controller = controller("any", "view", "edit");
        controller.authorizedKeys.add("view");

        assertTrue(controller.isAuthorize());
        assertEquals(List.of("view"), controller.checkedKeys);
    }

    @Test
    public void allModeRequiresEveryKey() {
        TestController controller = controller("all", "view", "edit");
        controller.authorizedKeys.add("view");

        assertFalse(controller.isAuthorize());
        assertEquals(List.of("view", "edit"), controller.checkedKeys);
    }

    @Test
    public void allModeAuthorizesWhenEveryKeyMatches() {
        TestController controller = controller("all", "view", "edit");
        controller.authorizedKeys.add("view");
        controller.authorizedKeys.add("edit");

        assertTrue(controller.isAuthorize());
    }

    @Test
    public void blankAndDuplicateRowsAreIgnored() {
        TestController controller = controller("any", " view ", "", "view", "edit");

        assertEquals(List.of("view", "edit"), controller.extractedKeys());
    }

    @Test
    public void emptyConfigurationFailsClosed() {
        TestController controller = controller("any");

        assertFalse(controller.isAuthorize());
    }

    private TestController controller(String matchMode, String... keys) {
        TestController controller = new TestController();
        Object[] rows = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Map<String, String> row = new HashMap<>();
            row.put("key", keys[i]);
            rows[i] = row;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("permissionKeys", rows);
        properties.put("matchMode", matchMode);
        controller.configure(properties);
        return controller;
    }

    private static class TestController extends MultiPermissionMenuController {
        private final List<String> authorizedKeys = new ArrayList<>();
        private final List<String> checkedKeys = new ArrayList<>();

        private void configure(Map<String, Object> configuredProperties) {
            properties = configuredProperties;
        }

        @Override
        protected boolean isAuthorizedForPermissionKey(String permissionKey) {
            checkedKeys.add(permissionKey);
            return authorizedKeys.contains(permissionKey);
        }

        private List<String> extractedKeys() {
            return getPermissionKeys();
        }
    }
}
