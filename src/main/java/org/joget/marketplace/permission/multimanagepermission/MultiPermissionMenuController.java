package org.joget.marketplace.permission.multimanagepermission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joget.apps.app.dao.EnvironmentVariableDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.EnvironmentVariable;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DatalistPermission;
import org.joget.apps.form.model.FormPermission;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.service.PropertyUtil;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;

/**
 * Authorizes against multiple keys created by Joget's Manage Permission menu.
 */
public class MultiPermissionMenuController extends UserviewPermission
        implements FormPermission, DatalistPermission {

    static final String PERMISSION_MENU_PREFIX = "PERMISSION_";
    static final String MATCH_ALL = "all";

    @Override
    public String getName() {
        return "Multi Manage Permission Controller";
    }

    @Override
    public String getVersion() {
        return "9.0.0";
    }

    @Override
    public String getLabel() {
        return "Controlled By Multiple Manage Permission Keys";
    }

    @Override
    public String getDescription() {
        return "Controls access using multiple keys configured in the Manage Permission menu.";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/userview/multiPermissionMenuController.json", null, true, "messages/multiPermissionMenuController");
    }

    @Override
    public boolean isAuthorize() {
        List<String> permissionKeys = getPermissionKeys();
        if (permissionKeys.isEmpty()) {
            return false;
        }

        boolean matchAll = MATCH_ALL.equalsIgnoreCase(getPropertyString("matchMode"));
        for (String permissionKey : permissionKeys) {
            boolean authorized = isAuthorizedForPermissionKey(permissionKey);

            if (!matchAll && authorized) {
                return true;
            }
            if (matchAll && !authorized) {
                return false;
            }
        }

        return matchAll;
    }

    /**
     * Extracts non-blank unique keys from the one-column property grid.
     */
    protected List<String> getPermissionKeys() {
        Object configuredKeys = getProperty("permissionKeys");
        Set<String> keys = new LinkedHashSet<>();

        if (configuredKeys instanceof Object[]) {
            for (Object row : (Object[]) configuredKeys) {
                addPermissionKey(keys, row);
            }
        } else if (configuredKeys instanceof Collection) {
            for (Object row : (Collection<?>) configuredKeys) {
                addPermissionKey(keys, row);
            }
        } else if (configuredKeys != null) {
            addPermissionKey(keys, configuredKeys);
        }

        return new ArrayList<>(keys);
    }

    private void addPermissionKey(Set<String> keys, Object row) {
        Object value = row;
        if (row instanceof Map) {
            value = ((Map<?, ?>) row).get("key");
        }

        if (value != null) {
            String key = value.toString().trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
    }

    /**
     * Evaluates one key using the same environment-variable format and cache key
     * used by Joget's built-in Manage Permission controller.
     */
    protected boolean isAuthorizedForPermissionKey(String permissionKey) {
        String environmentKey = PERMISSION_MENU_PREFIX + permissionKey.replace('.', '_');
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        Boolean authorized = (Boolean) workflowUserManager.getCurrentUserTempData(environmentKey);

        if (authorized == null) {
            authorized = Boolean.FALSE;

            try {
                AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
                EnvironmentVariableDao environmentVariableDao = (EnvironmentVariableDao) AppUtil.getApplicationContext().getBean("environmentVariableDao");
                EnvironmentVariable environmentVariable = environmentVariableDao.loadById(environmentKey, appDefinition);

                if (environmentVariable != null
                        && environmentVariable.getValue() != null
                        && !environmentVariable.getValue().isEmpty()) {
                    authorized = evaluatePermissionValue(environmentVariable.getValue());
                }
            } catch (Exception exception) {
                LogUtil.error(getClassName(), exception, "Unable to evaluate Manage Permission key: " + permissionKey);
            }

            workflowUserManager.setCurrentUserTempData(environmentKey, authorized);
        }

        return authorized;
    }

    protected boolean evaluatePermissionValue(String value) {
        if (!(value.startsWith("{") && value.endsWith("}"))) {
            String username = WorkflowUtil.getCurrentUsername();
            List<String> usernames = new ArrayList<>(Arrays.asList(value.split(";")));
            return usernames.contains(username);
        }

        Map<String, Object> storedProperties = PropertyUtil.getPropertiesValueFromJson(value);
        Object permissionData = storedProperties.get("permission");
        if (!(permissionData instanceof Map)) {
            return false;
        }

        Map<?, ?> permissionMap = (Map<?, ?>) permissionData;
        Object classNameValue = permissionMap.get("className");
        String className = classNameValue == null ? "" : classNameValue.toString().trim();
        if (className.isEmpty()) {
            return false;
        }

        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Plugin plugin = pluginManager.getPlugin(className);
        if (!(plugin instanceof UserviewPermission)) {
            return false;
        }

        UserviewPermission permission = (UserviewPermission) plugin;
        Map<String, Object> properties = new HashMap<>();
        Object propertiesValue = permissionMap.get("properties");
        if (propertiesValue instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) propertiesValue).entrySet()) {
                if (entry.getKey() != null) {
                    properties.put(entry.getKey().toString(), entry.getValue());
                }
            }
        }

        permission.setProperties(properties);
        permission.setRequestParameters(getRequestParameters());
        permission.setCurrentUser(getCurrentUser());
        return permission.isAuthorize();
    }
}
