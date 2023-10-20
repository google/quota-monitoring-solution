/*
Copyright 2023 Google LLC

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package functions;

import com.google.cloud.ReadChannel;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.CsvOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.LoadJobConfiguration;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.MetricInfo;
import com.google.cloud.monitoring.v3.AlertPolicyServiceClient;
import com.google.cloud.monitoring.v3.NotificationChannelServiceClient;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.monitoring.v3.Aggregation;
import com.google.monitoring.v3.Aggregation.Aligner;
import com.google.monitoring.v3.AlertPolicy;
import com.google.monitoring.v3.AlertPolicy.Condition;
import com.google.monitoring.v3.AlertPolicy.Condition.MetricThreshold;
import com.google.monitoring.v3.AlertPolicy.Condition.Trigger;
import com.google.monitoring.v3.AlertPolicy.ConditionCombinerType;
import com.google.monitoring.v3.AlertPolicy.Documentation;
import com.google.monitoring.v3.ComparisonType;
import com.google.monitoring.v3.NotificationChannel;
import com.google.monitoring.v3.UpdateAlertPolicyRequest;
import com.google.monitoring.v3.UpdateNotificationChannelRequest;
import com.google.protobuf.Duration;
import functions.eventpojos.AppAlert;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import com.google.monitoring.v3.ProjectName;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.apache.commons.lang3.StringUtils;


public class ConfigureAppAlertHelper {
  private static final String HOME_PROJECT = ConfigureAppAlert.HOME_PROJECT;
  private static final String DATASET = ConfigureAppAlert.APP_ALERT_DATASET;
  private static final String TABLE = ConfigureAppAlert.APP_ALERT_TABLE;
  private static final String CSV_SOURCE_URI = ConfigureAppAlert.CSV_SOURCE_URI;

  private static final Logger logger = LoggerFactory.getLogger(ConfigureAppAlertHelper.class);



  /*
   * API to initialize App Alert table.
   * This API loads data from csv file in Cloud Storage bucket to BigQuery table
   * */
  public static void loadCsvFromGcsToBigQuery(BigQuery bigquery, TableId tableId){
    // Do not upload data from csv if table already contains data
    // This batch upload is used only for initializing the data for the first time
    // for subsequent addition/modification of data, use DML queries in BigQuery table
    if(!isAppAlertTableEmpty(bigquery) )
      return;
    // Parse the csv file and verify that
    // 1. File is available,
    // 2. There are no records with empty/null project_id, email_id or app_code
    // 3. There are no duplicates for app_code
    if(isCSVParseSuccess()){
      MDC.put("severity", "INFO");
      logger.info("CSV parsed successfully! Loading data in BigQuery...");
    } else {
      MDC.put("severity", "SEVERE");
      logger.severe("CSV parsing failed. Fix the CSV to proceed.");
      return;
    }

    //Upload data from csv if table is empty
    Schema schema =
        Schema.of(
            Field.of("project_id", StandardSQLTypeName.STRING),
            Field.of("email_id", StandardSQLTypeName.STRING),
            Field.of("app_code", StandardSQLTypeName.STRING),
            Field.of("dashboard_url", StandardSQLTypeName.STRING));
    try {
      // Skip header row in the file.
      CsvOptions csvOptions = CsvOptions.newBuilder().setSkipLeadingRows(1).build();

      LoadJobConfiguration loadConfig =
          LoadJobConfiguration.newBuilder(tableId, CSV_SOURCE_URI, csvOptions).setSchema(schema).build();

      // Load data from a GCS CSV file into the table
      Job job = bigquery.create(JobInfo.of(loadConfig));
      // Blocks until this load table job completes its execution, either failing or succeeding.
      job = job.waitFor();
      if (job.isDone()) {
        MDC.put("severity", "INFO");
        logger.info("CSV from GCS successfully added during load append job");
      } else {
        MDC.put("severity", "SEVERE");
        logger.severe(
            "BigQuery was unable to load into the table due to an error:"
                + job.getStatus().getError());
      }
    } catch (BigQueryException | InterruptedException e) {
      MDC.put("severity", "SEVERE");
      logger.severe("Column not added during load append \n" + e.toString());
    }
  }

  /*
   * API to configure App Alerting - Custom log metrics, Notification Channels and Alert Policies for each app
   * */
  public static void configureAppAlerting(BigQuery bigquery){
    // Fetch all records for app alert from DB and list
    List<AppAlert> appAlerts = listAppAlertConfig(bigquery);

    // If any of the appCode is violating unique constraint for appCode, return
    if(!appCodeUnique(appAlerts))
      return;

    //Iterate over AppAlerts and create or update configurations for Custom Log Metrics, Notification Channels and Alert Policies
    //Update configuration in BigQuery table for each appAlert entity
    for(AppAlert appAlert : appAlerts){
      appAlert.setCustomLogMetricId(createCustomLogMetric(appAlert));
      appAlert.setNotificationChannelId(createOrUpdateNotificationChannel(appAlert));
      appAlert.setAlertPolicyId(createOrUpdateAlertPolicy(appAlert));
      updateAppAlertConfig(bigquery, appAlert);
    }
    MDC.put("severity", "INFO");
    logger.info("App Alert configuration completed successfully for all apps!");
  }

  /*
   * API to verify that appCodes are unique in DB
   * */
  private static boolean appCodeUnique(List<AppAlert> appAlerts){
    Set<String> appCodes = new HashSet<>();

    for(AppAlert appAlert : appAlerts){
      String appCode  = appAlert.getAppCode();
      //If AppCode is not unique, return false
      if(!appCodes.add(appCode)){
        MDC.put("severity", "SEVERE");
        logger.severe("Found duplicate app code \""+appCode+"\" in BigQuery Table. app_code should be unique for each row" );
        return false;
      }
    }
    return true;
  }

  /*
   * API to fetch App Alert configurations from BigQuery
   * @return - List of App Alerts
   * */
  public static List<AppAlert> listAppAlertConfig(BigQuery bigquery){
    List<AppAlert> appAlerts = new ArrayList<>();
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(
                "SELECT * "
                    + "FROM `"
                    + HOME_PROJECT
                    + "."
                    + DATASET
                    + "."
                    + TABLE
                    + "` ")
            .setUseLegacySql(false)
            .build();

    TableResult result = executeBigQueryQuery(bigquery, queryConfig);

    // Get all pages of the results
    for (FieldValueList row : result.iterateAll()) {
      // Get all values
      AppAlert appAlert = new AppAlert();
      appAlert.setProjectId(row.get("project_id").getStringValue());
      appAlert.setEmailId(row.get("email_id").getStringValue());
      appAlert.setAppCode(row.get("app_code").getStringValue());
      appAlert.setDashboardUrl(row.get("dashboard_url").isNull() ? null : row.get("dashboard_url").getStringValue());
      appAlert.setNotificationChannelId(row.get("notification_channel_id").isNull() ? null : row.get("notification_channel_id").getStringValue());
      appAlert.setCustomLogMetricId(row.get("custom_log_metric_id").isNull() ? null : row.get("custom_log_metric_id").getStringValue());
      appAlert.setAlertPolicyId(row.get("alert_policy_id").isNull() ? null : row.get("alert_policy_id").getStringValue());
      appAlerts.add(appAlert);
    }
    MDC.put("severity", "INFO");
    logger.info("Query ran successfully to list App Alerts!");

    return appAlerts;
  }

  /*
   * API to update App Alerting by adding Custom log metric Id, Notification Channel Id and Alert Policy Id for each app
   * */
  private static void updateAppAlertConfig(BigQuery bigquery, AppAlert appAlert){
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(
                "UPDATE "
                    + "`"
                    + HOME_PROJECT
                    + "."
                    + DATASET
                    + "."
                    + TABLE
                    + "` "
                    +" SET custom_log_metric_id = \""+appAlert.getCustomLogMetricId()+"\", notification_channel_id = \""+appAlert.getNotificationChannelId()+"\", alert_policy_id = \""+appAlert.getAlertPolicyId()+"\" "
                    +" WHERE app_code = \""+appAlert.getAppCode()+"\""
            )
            .setUseLegacySql(false)
            .build();

    TableResult result = executeBigQueryQuery(bigquery, queryConfig);

    /*// Print the results.
    result.iterateAll().forEach(rows -> rows.forEach(row -> logger.info((String) row.getValue())));*/
    MDC.put("severity", "INFO");
    logger.info("Table updated successfully and updated Alert Config for App Alerts");
  }


  /*
   * API to configure Custom log metrics
   * @param appAlert - appCode name from appAlert
   * @return CustomLogMetric name
   * */
  private static String createCustomLogMetric(AppAlert appAlert){
    // If custom log metric has been created for the appId, then do not create a new custom log metric
    if(appAlert.getCustomLogMetricId() != null)
      return appAlert.getCustomLogMetricId();

    // Create a custom log metric for the appId if metric doesn't exist for appCode
    LoggingOptions options = LoggingOptions.getDefaultInstance();
    MetricInfo metricInfo = null;
    try(Logging logging = options.getService()) {

      metricInfo = MetricInfo.newBuilder("resource_usage_"+appAlert.getAppCode(), "logName:\"projects/"+HOME_PROJECT+"/logs/\" jsonPayload.message:\"|AppCode-"+appAlert.getAppCode()+" | ProjectId | Scope |\"")
          .setDescription("Tracks logs for quota usage above threshold for app_code = "+appAlert.getAppCode())
          .build();
      metricInfo = logging.create(metricInfo);
    } catch (Exception e) {
      MDC.put("severity", "SEVERE");
      logger.severe("Error creating Custom Log Metric for app code - "+appAlert.getAppCode()+e.getMessage());
    }
    MDC.put("severity", "INFO");
    logger.info("Successfully created custom log metric - "+ ((metricInfo == null ) ? null : metricInfo.getName()));
    return metricInfo.getName();
  }

  /*
   * API to create or update Notification Channels for each app
   * */
  private static String createOrUpdateNotificationChannel(AppAlert appAlert){
    NotificationChannel notificationChannel = null;
    try (NotificationChannelServiceClient client = NotificationChannelServiceClient.create()) {
      // Create a new notification channel if there is no existing notification channel for the given appCode
      if(appAlert.getNotificationChannelId() == null){
        notificationChannel = createNotificationChannel(client, appAlert);
      } else {
        // Update the notification channel if there is an existing notification channel for the given appCode
        // This can be used to update the emailId
        notificationChannel = updateNotificationChannel(client, appAlert);
      }
    } catch (IOException e) {
      MDC.put("severity", "SEVERE");
      logger.severe("Can't create Notification channel "+e.toString());
    }
    return ((notificationChannel == null ) ? null : notificationChannel.getName());
  }

  /*
   * API to create Notification Channels for a given app Code and email id
   * */
  private static NotificationChannel createNotificationChannel(NotificationChannelServiceClient client, AppAlert appAlert){
    NotificationChannel notificationChannel = NotificationChannel.newBuilder()
        .setType("email")
        .setDisplayName("OnCall-"+appAlert.getAppCode())
        .setDescription("Email channel for alert notification on app -"+appAlert.getAppCode())
        .putLabels("email_address",appAlert.getEmailId())
        .build();

    notificationChannel = client.createNotificationChannel("projects/"+HOME_PROJECT,notificationChannel);
    MDC.put("severity", "INFO");
    logger.info("Successfully created notification channel - "+notificationChannel.getName());
    return notificationChannel;
  }

  /*
   * API to update Notification Channels for a given app Code and email id
   * */
  private static NotificationChannel updateNotificationChannel(NotificationChannelServiceClient client, AppAlert appAlert){
    NotificationChannel notificationChannel = NotificationChannel.newBuilder()
        .setType("email")
        .setDisplayName("OnCall-"+appAlert.getAppCode())
        .setDescription("Email channel for alert notification on app -"+appAlert.getAppCode())
        .putLabels("email_address",appAlert.getEmailId())
        .setName(appAlert.getNotificationChannelId())
        .build();

    UpdateNotificationChannelRequest updateNotificationChannelRequest  =
        UpdateNotificationChannelRequest.newBuilder().setNotificationChannel(notificationChannel).build();
    notificationChannel = client.updateNotificationChannel(updateNotificationChannelRequest);
    MDC.put("severity", "INFO");
    logger.info("Successfully updated notification channel - "+notificationChannel.getName());
    return notificationChannel;
  }

  /*
   * API to create or update Alert Policy for a given app Code
   * */
  private static String createOrUpdateAlertPolicy(AppAlert appAlert) {
    AlertPolicy actualAlertPolicy = null;
    try (AlertPolicyServiceClient alertPolicyServiceClient = AlertPolicyServiceClient.create()) {

      // A Filter that identifies which time series should be compared with the threshold
      String metricFilter =
          "resource.type = \"cloud_function\" AND metric.type=\"logging.googleapis.com/user/"
              + appAlert.getCustomLogMetricId() + "\"";

      // Build Duration
      Duration aggregationDuration = Duration.newBuilder().setSeconds(60).build();

      //Build Documentation
      Documentation documentation = Documentation.newBuilder()
          .setMimeType("text/markdown")
          .setContent("**Resource usage quota is reaching threshold in project - "+appAlert.getProjectId()+"<br /> [See Quota Dashboard for details]("+appAlert.getDashboardUrl()+")**")
          .build();

      // Build Aggregation
      Aggregation aggregation =
          Aggregation.newBuilder()
              .setAlignmentPeriod(aggregationDuration)
              .setPerSeriesAligner(Aligner.ALIGN_COUNT)
              .build();

      // Build MetricThreshold
      AlertPolicy.Condition.MetricThreshold metricThreshold =
          MetricThreshold.newBuilder()
              .setComparison(ComparisonType.COMPARISON_GT)
              .addAggregations(aggregation)
              .setFilter(metricFilter)
              .setDuration(aggregationDuration)
              .setTrigger(Trigger.newBuilder().setCount(1).build())
              .build();

      // Construct Condition object
      AlertPolicy.Condition alertPolicyCondition =
          AlertPolicy.Condition.newBuilder()
              .setDisplayName("QuotaExceedAlertPolicy-" + appAlert.getAppCode())
              .setConditionThreshold(metricThreshold)
              .build();

      // Create an alert policy
      if(appAlert.getAlertPolicyId() == null){
        actualAlertPolicy = createAlertPolicy(alertPolicyServiceClient, appAlert, documentation, alertPolicyCondition);
      } else {
        //Update Alert policy
        actualAlertPolicy = updateAlertPolicy(alertPolicyServiceClient, appAlert, documentation, alertPolicyCondition);
      }


    } catch (IOException e) {
      MDC.put("severity", "SEVERE");
      logger.severe("Error creating or updating Alert Policy for app code - "+appAlert.getAppCode()+e.getMessage());
    }
    return ((actualAlertPolicy == null) ? null : actualAlertPolicy.getName());

  }

  /*
   * API to create an Alert Policy for a given app Code
   * */
  private static AlertPolicy createAlertPolicy(AlertPolicyServiceClient client, AppAlert appAlert, Documentation documentation, Condition alertPolicyCondition){
    AlertPolicy alertPolicy =
        AlertPolicy.newBuilder()
            .setDisplayName("QuotaExceedAlertPolicy-" + appAlert.getAppCode())
            .setDocumentation(documentation)
            .addConditions(alertPolicyCondition)
            .setCombiner(ConditionCombinerType.OR)
            .addNotificationChannels(appAlert.getNotificationChannelId())
            .build();

    AlertPolicy actualAlertPolicy = client.createAlertPolicy(ProjectName.of(HOME_PROJECT), alertPolicy);
    MDC.put("severity", "INFO");
    logger.info("alert policy created successfully - "+ actualAlertPolicy.getName());
    return actualAlertPolicy;
  }

  /*
   * API to update an Alert Policy for a given app Code
   * */
  private static AlertPolicy updateAlertPolicy(AlertPolicyServiceClient client, AppAlert appAlert, Documentation documentation, Condition alertPolicyCondition){
    AlertPolicy alertPolicy1 =
        AlertPolicy.newBuilder()
            .setName(appAlert.getAlertPolicyId())
            .setDisplayName("QuotaExceedAlertPolicy-" + appAlert.getAppCode())
            .setDocumentation(documentation)
            .addConditions(alertPolicyCondition)
            .setCombiner(ConditionCombinerType.OR)
            .addNotificationChannels(appAlert.getNotificationChannelId())
            .build();

    UpdateAlertPolicyRequest updateAlertPolicyRequest =
        UpdateAlertPolicyRequest.newBuilder().setAlertPolicy(alertPolicy1).build();
    AlertPolicy actualAlertPolicy = client.updateAlertPolicy(updateAlertPolicyRequest);
    MDC.put("severity", "INFO");
    logger.info("alert policy updated successfully - "+ actualAlertPolicy.getName());
    return actualAlertPolicy;
  }

  /*
   * API to check that table is empty before initializing the data
   * */
  private static boolean isAppAlertTableEmpty(BigQuery bigquery){
    int count = 0;
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(
                "SELECT COUNT (*) as count "
                    + "FROM `"
                    + HOME_PROJECT
                    + "."
                    + DATASET
                    + "."
                    + TABLE
                    + "` ")
            .setUseLegacySql(false)
            .build();

    TableResult result = executeBigQueryQuery(bigquery, queryConfig);

    for (FieldValueList row : result.iterateAll()) {
      count  = (int) row.get("count").getLongValue();
    }
    //Return true is table contains zero records else return false
    return count <= 0;
  }

  /*
   * API to execute parse CSV file before uploading to BigQuery for App Alert data initialization
   * 1. Check that file exists
   * 2. Check if each record contains projectId, emailId and appCode
   * 3. Check that appCode is not duplicate
   * */
  private static boolean isCSVParseSuccess(){

    BufferedReader bufferedReader = null;
    String row = "";
    Set<String> appCodes = new HashSet<>();

    Storage storage = StorageOptions.getDefaultInstance().getService();
    String bucketName = StringUtils.substringBetween(CSV_SOURCE_URI, "//", "/");
    String fileName = StringUtils.substringAfterLast(CSV_SOURCE_URI, "/");
    Blob blob = storage.get(bucketName,fileName);
    //If csv file is not found in the cloud storage bucket, return
    if(blob == null){
      MDC.put("severity", "SEVERE");
      logger.severe("CSV file not found - "+CSV_SOURCE_URI);
      return false;
    }
    ReadChannel readChannel = blob.reader();
    try {
      bufferedReader = new BufferedReader(Channels.newReader(readChannel, "UTF-8"));
      while ((row = bufferedReader.readLine()) != null) {
        String[] cells = row.split(",");

        //2. Check if each record contains projectId, emailId and appCode
        if(StringUtils.isEmpty(cells[0]) || StringUtils.isEmpty(cells[1]) || StringUtils.isEmpty(cells[2])){
          MDC.put("severity", "SEVERE");
          logger.severe("Found empty record in CSV. Can't load csv in BigQuery. Required project_id, email_id and app_code in each rows");
          return false;
        }

        //3. Check that appCode is not duplicate
        if(!appCodes.add(cells[2])) {
          MDC.put("severity", "SEVERE");
          logger.severe("Found duplicate app code \""+cells[2]+"\" in CSV. Can't load csv in BigQuery. app_code should be unique for each row");
          return false;
        }
      }
    } catch (FileNotFoundException e) {
      MDC.put("severity", "SEVERE");
      logger.severe("File not found - "+CSV_SOURCE_URI+e.getMessage());
      return false;
    } catch (IOException e) {
      MDC.put("severity", "SEVERE");
      logger.severe("Error parsing CSV file - "+CSV_SOURCE_URI+e.getMessage());
      return false;
    }
    return true;
  }


  /*
   * API to execute DML query on BigQuery table for the given query
   * */
  private static TableResult executeBigQueryQuery(BigQuery bigquery, QueryJobConfiguration queryConfig){
    TableResult result = null;
    try{
      // Create a job ID so that we can safely retry.
      JobId jobId = JobId.of(UUID.randomUUID().toString());
      Job queryJob = bigquery.create(JobInfo.newBuilder(queryConfig).setJobId(jobId).build());

      // Wait for the query to complete.
      queryJob = queryJob.waitFor();

      // Check for errors
      if (queryJob == null) {
        throw new RuntimeException("Job no longer exists");
      } else if (queryJob.getStatus().getError() != null) {
        throw new RuntimeException(queryJob.getStatus().getError().toString());
      }

      // Identify the table
      result = queryJob.getQueryResults();
    } catch (InterruptedException e) {
      MDC.put("severity", "SEVERE");
      logger.severe("Error executing BigQuery query"+e.getMessage());
    }
    return result;
  }

}
