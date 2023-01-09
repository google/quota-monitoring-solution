package functions;

import static functions.ConfigureAppAlertHelper.configureAppAlerting;
import static functions.ConfigureAppAlertHelper.loadCsvFromGcsToBigQuery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import java.util.logging.Logger;

public class ConfigureAppAlert implements HttpFunction {
  public static final String HOME_PROJECT = "quota-monitoring-project-78";//System.getenv("HOME_PROJECT");
  public static final String APP_ALERT_DATASET = "quota_monitoring_notification_dataset";//System.getenv("APP_ALERT_DATASET");
  public static final String APP_ALERT_TABLE = "quota_monitoring_decentralize_alerting_table";//System.getenv("APP_ALERT_TABLE");
  public static final String CSV_SOURCE_URI = "gs://quota-monitoring-project-78-gcf-source/QMS_app_alerting.csv";//System.getenv("CSV_SOURCE_URI");

  private static final Logger logger = Logger.getLogger(ConfigureAppAlert.class.getName());

  @Override
  public void service(HttpRequest request, HttpResponse response)
      throws Exception {
    response.getWriter().write("App Notification Configuration starting\n");
    // Initialize client that will be used to send requests.
    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    // Get table
    TableId tableId = TableId.of(HOME_PROJECT, APP_ALERT_DATASET, APP_ALERT_TABLE);
    //Initialize App Alert Table - Load data from CSV file
    loadCsvFromGcsToBigQuery(bigquery,tableId);
    // Configure App Alerting - Custom Log Metrics, Notification Channels and Alert Policies
    configureAppAlerting(bigquery);
  }


}