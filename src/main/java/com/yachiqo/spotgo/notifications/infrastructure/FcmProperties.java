package com.yachiqo.spotgo.notifications.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications.fcm")
public class FcmProperties {
    private boolean enabled;
    private String projectId = "";
    private String serviceAccountJson = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getServiceAccountJson() { return serviceAccountJson; }
    public void setServiceAccountJson(String serviceAccountJson) { this.serviceAccountJson = serviceAccountJson; }
}
