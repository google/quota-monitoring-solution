package functions;

import static functions.ConfigureAppAlertHelper.configureAppAlerting;
import static functions.ConfigureAppAlertHelper.loadCsvFromGcsToBigQuery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

public class ConfigureAppNotification implements HttpFunction {
  private static final String HOME_PROJECT = "quota-monitoring-project-78";//System.getenv("HOME_PROJECT");
  private static final String DATASET = "quota_monitoring_notification_dataset";//System.getenv("ALERT_DATASET");
  private static final String TABLE = "quota_monitoring_decentralize_alerting_table";//System.getenv("ALERT_TABLE");
  private static final String CSV_SOURCE_URI = "gs://quota-monitoring-project-78-gcf-source/QMS_app_alerting.csv";//System.getenv("CSV_SOURCE_URI");

  @Override
  public void service(HttpRequest request, HttpResponse response)
      throws Exception {
    response.getWriter().write("App Notification Configuration starting\n");
    // Initialize client that will be used to send requests.
    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    // Get table
    TableId tableId = TableId.of(HOME_PROJECT,DATASET, TABLE);
    //Initialize App Alert Table - Load data from CSV file
    loadCsvFromGcsToBigQuery(bigquery,tableId);
    // Configure App Alerting - Custom Log Metrics, Notification Channels and Alert Policies
    configureAppAlerting(bigquery);
  }


}