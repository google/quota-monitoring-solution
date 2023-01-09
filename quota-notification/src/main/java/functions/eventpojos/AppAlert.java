package functions.eventpojos;

public class AppAlert {
  private String projectId;
  private String emailId;
  private String appCode;
  private String dashboardUrl;
  private String notificationChannelId;
  private String customLogMetricId;
  private String alertPolicyId;

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getEmailId() {
    return emailId;
  }

  public void setEmailId(String emailId) {
    this.emailId = emailId;
  }

  public String getAppCode() {
    return appCode;
  }

  public void setAppCode(String appCode) {
    this.appCode = appCode;
  }

  public String getDashboardUrl() {
    return dashboardUrl;
  }

  public void setDashboardUrl(String dashboardUrl) {
    this.dashboardUrl = dashboardUrl;
  }

  public String getNotificationChannelId() {
    return notificationChannelId;
  }

  public void setNotificationChannelId(String notificationChannelId) {
    this.notificationChannelId = notificationChannelId;
  }

  public String getCustomLogMetricId() {
    return customLogMetricId;
  }

  public void setCustomLogMetricId(String customLogMetricId) {
    this.customLogMetricId = customLogMetricId;
  }

  public String getAlertPolicyId() {
    return alertPolicyId;
  }

  public void setAlertPolicyId(String alertPolicyId) {
    this.alertPolicyId = alertPolicyId;
  }
}
