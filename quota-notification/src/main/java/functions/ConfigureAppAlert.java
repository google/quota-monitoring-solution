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

import static functions.ConfigureAppAlertHelper.configureAppAlerting;
import static functions.ConfigureAppAlertHelper.loadCsvFromGcsToBigQuery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import functions.eventpojos.GcsEvent;
import java.util.logging.Logger;

public class ConfigureAppAlert implements BackgroundFunction<GcsEvent> {
  public static final String HOME_PROJECT = System.getenv("HOME_PROJECT");
  public static final String APP_ALERT_DATASET = System.getenv("APP_ALERT_DATASET");
  public static final String APP_ALERT_TABLE = System.getenv("APP_ALERT_TABLE");
  public static final String CSV_SOURCE_URI = System.getenv("CSV_SOURCE_URI");

  private static final Logger logger = Logger.getLogger(ConfigureAppAlert.class.getName());

  @Override
  public void accept(GcsEvent event, Context context) throws Exception {
    logger.info("App Notification Configuration starting\n");

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