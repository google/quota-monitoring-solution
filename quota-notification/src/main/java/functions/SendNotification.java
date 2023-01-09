/*
Copyright 2022 Google LLC

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

import static functions.ConfigureAppAlertHelper.listAppAlertConfig;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import functions.eventpojos.Alert;
import functions.eventpojos.AppAlert;
import functions.eventpojos.PubSubMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/*
 * Cloud Function triggered by Pub/Sub topic to send notification
 * */
public class SendNotification implements BackgroundFunction<PubSubMessage> {
  private static final String HOME_PROJECT = System.getenv("HOME_PROJECT");
  private static final String DATASET = System.getenv("ALERT_DATASET");
  private static final String TABLE = System.getenv("ALERT_TABLE");
  private static final String APP_ALERT_DATASET = System.getenv("APP_ALERT_DATASET");
  private static final String APP_ALERT_TABLE = System.getenv("APP_ALERT_TABLE");

  private static final Logger logger = Logger.getLogger(SendNotification.class.getName());

  /*
   * API to accept notification information and process it
   * */
  @Override
  public void accept(PubSubMessage message, Context context) {
    // Initialize client that will be used to send requests
    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    // logger.info(String.format(message.getEmailIds()));
    logger.info("Successfully made it to sendNotification");
    List<Alert> alerts = browseAlertTable(bigquery);
    logger.info("Successfully got data from alert table");
    String alertMessage = buildAlertMessage(alerts, null);
    logger.info(alertMessage);
    appAlertLogs(bigquery, alerts);
    return;
  }

  /*
   * API to fetch records which qualifies for alerting from the main table
   * */
  private static List<Alert> browseAlertTable(BigQuery bigquery) {
    List<Alert> alerts = new ArrayList();
    Alert alert = new Alert();
    try {

      QueryJobConfiguration queryConfig =
          QueryJobConfiguration.newBuilder(
                  "SELECT project_id, region, metric, usage,q_limit, consumption "
                      + "FROM `"
                      + HOME_PROJECT
                      + "."
                      + DATASET
                      + "."
                      + TABLE
                      + "` ")
              .setUseLegacySql(false)
              .build();

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
      TableResult result = queryJob.getQueryResults();

      // Get all pages of the results
      for (FieldValueList row : result.iterateAll()) {
        // Get all values
        alert = new Alert();
        alert.setProjectId(row.get("project_id").getStringValue());
        alert.setRegion(row.get("region").getStringValue());
        alert.setMetric(row.get("metric").getStringValue());
        alert.setLimit(row.get("q_limit").getStringValue());
        alert.setUsage(row.get("usage").getStringValue());
        alert.setConsumption(row.get("consumption").getNumericValue().floatValue());

        alerts.add(alert);
      }
      logger.info("Query ran successfully ");
    } catch (BigQueryException | InterruptedException e) {
      logger.severe("Query failed to run \n" + e.toString());
    }
    return alerts;
  }

  /*
   * API to build Alert Message for list of Quota metrics
   * */
  private static String buildAlertMessage(List<Alert> alerts, String appCode){
    StringBuilder htmlBuilder = new StringBuilder();
    htmlBuilder.append("Quota metric usage alert details\n\n");
    htmlBuilder.append("## "+alerts.size()+" quota metric usages above threshold\n\n");
    if(appCode == null)
      htmlBuilder.append("|ProjectId | Scope | Metric  | Consumption(%) |\n");
    else
      htmlBuilder.append("|AppCode-"+appCode+" | ProjectId | Scope | Metric  | Consumption(%) |\n");
    htmlBuilder.append("|:---------|:------|:--------|:---------------|\n");
    for(Alert alert : alerts){
      htmlBuilder.append(alert.toString());
      htmlBuilder.append("|\n");
    }
    String html = htmlBuilder.toString();
    return html;
  }

  private static void appAlertLogs(BigQuery bigquery, List<Alert> alerts){
    List<AppAlert> appAlertsConfigs = listAppAlertConfig(bigquery);
    Map<String, String> appAlertsConfigsMap = new HashMap<>();

    //Convert List to Map
    for(AppAlert appAlertConfig : appAlertsConfigs){
      appAlertsConfigsMap.put(appAlertConfig.getProjectId(), appAlertConfig.getAppCode());
    }

    for(Alert alert : alerts){
      String appCode = appAlertsConfigsMap.get(alert.getProjectId());
      if (appAlertsConfigsMap.containsKey(alert.getProjectId())){
        String alertMessage = buildAlertMessage(alerts, appCode);
        logger.info(alertMessage);
      }
    }
  }
}
