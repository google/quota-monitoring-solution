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

import static functions.ScanProjectQuotas.THRESHOLD;
import static functions.ScanProjectQuotas.TIME_INTERVAL_START;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.MetricServiceClient.ListTimeSeriesPagedResponse;
import com.google.monitoring.v3.ListTimeSeriesRequest;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.util.Timestamps;

import functions.eventpojos.GCPProject;
import functions.eventpojos.GCPResourceClient;
import functions.eventpojos.ProjectQuota;
import functions.eventpojos.TimeSeriesQuery;
import functions.eventpojos.MetricFix;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScanProjectQuotasHelper {
  // Cloud Function Environment variable to identify usage and limit values
  public static final String METRIC_VALUE_USAGE = "usage";
  public static final String METRIC_VALUE_LIMIT = "limit";
  public static final MetricFix[] metricFixList = new MetricFix[] {new MetricFix("storage.googleapis.com/google_egress_bandwidth", 60)};
  private static final Logger logger = Logger.getLogger(ScanProjectQuotasHelper.class.getName());

  /*
   * API to create GCP Resource Client for BigQuery Tables
   * */
  static GCPResourceClient createGCPResourceClient() {
    String datasetName = ScanProjectQuotas.BIG_QUERY_DATASET;
    String tableName = ScanProjectQuotas.BIG_QUERY_TABLE;
    // Initialize client that will be used to send requests.
    BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
    // Get table
    TableId tableId = TableId.of(datasetName, tableName);
    GCPResourceClient gcpResourceClient = new GCPResourceClient();
    gcpResourceClient.setBigQuery(bigquery);
    gcpResourceClient.setTableId(tableId);
    return gcpResourceClient;
  }

  /*
   * API to load Time Series Filters from the properties file
   * */
  static TimeSeriesQuery getTimeSeriesFilter() {
    TimeSeriesQuery timeSeriesQuery = new TimeSeriesQuery();
    try {
      InputStream input = ScanProjectQuotasHelper.class.getResourceAsStream("/config.properties");
      Properties prop = new Properties();
      // load a properties file
      prop.load(input);
      // get the property value and print it out
      timeSeriesQuery.setAllocationQuotaUsageFilter(
          prop.getProperty("allocation.quota.usage.filter"));
      timeSeriesQuery.setRateQuotaUsageFilter(prop.getProperty("rate.quota.usage.filter"));
      timeSeriesQuery.setQuotaLimitFilter(prop.getProperty("quota.limit.filter"));
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error reading properties file" + e.getMessage(), e);
    }
    return timeSeriesQuery;
  }

  /*
   * API to get Quota from Time Series APIs with filters
   * */
  static List<ProjectQuota> getQuota(GCPProject gcpProject, String filter, Boolean isLimit) {
    List<ProjectQuota> projectQuotas = new ArrayList<>();

    try (MetricServiceClient metricServiceClient = MetricServiceClient.create()) {
      TimeInterval interval = getTimeInterval();
      // Prepares the list time series request with headers
      ListTimeSeriesRequest request =
          ListTimeSeriesRequest.newBuilder()
              .setName(gcpProject.getProjectName())
              .setFilter(filter)
              .setInterval(interval)
              .build();

      // Send the request to list the time series
      ListTimeSeriesPagedResponse response = metricServiceClient.listTimeSeries(request);
      for (TimeSeries ts : response.iterateAll()) {
        projectQuotas.add(populateProjectQuota(ts, gcpProject.getProjectId(), isLimit));
      }

    } catch (IOException e) {
      logger.log(
          Level.SEVERE,
          "Error fetching timeseries data for project: " + gcpProject.getProjectName() + e.getMessage(),
          e);
    }

    return projectQuotas;
  }

  /*
   * API to build time interval for Time Series query
   * */
  private static TimeInterval getTimeInterval() {
    long startMillis = System.currentTimeMillis() - TIME_INTERVAL_START;
    TimeInterval interval =
        TimeInterval.newBuilder()
            .setStartTime(Timestamps.fromMillis(startMillis))
            .setEndTime(Timestamps.fromMillis(System.currentTimeMillis()))
            .build();
    return interval;
  }

  /*
   * API to load data into BigQuery
   * */
  static void loadBigQueryTable(
      GCPResourceClient gcpResourceClient,
      List<ProjectQuota> projectQuotas) {
    for (ProjectQuota pq : projectQuotas) {
      Map<String, Object> row = createBQRow(pq);
      tableInsertRows(gcpResourceClient, row);
    }
  }

  /*
   * API to populate Project Quota object from Time Series API response
   * */
  private static ProjectQuota populateProjectQuota(
      TimeSeries ts, String projectId, Boolean isLimit) {
    
    Map.Entry<FieldDescriptor, Object> entry =
        ts.getPointsList().get(0).getValue().getAllFields().entrySet().iterator().next();
    ProjectQuota projectQuota = new ProjectQuota();
    projectQuota.setThreshold(Integer.valueOf(THRESHOLD));
    projectQuota.setOrgId("orgId");
    projectQuota.setProjectId(projectId);
    projectQuota.setTimestamp("AUTO");
    projectQuota.setFolderId("NA");
    projectQuota.setTargetPoolName("NA");
    projectQuota.setRegion(ts.getResource().getLabelsMap().get("location"));
    projectQuota.setMetric(ts.getMetric().getLabelsMap().get("quota_metric"));

    if (isLimit) {
      projectQuota.setMetricValue(entry.getValue().toString());
      projectQuota.setMetricValueType(METRIC_VALUE_LIMIT);
    } else {
      boolean flag = false;
      MetricFix metricFix = new MetricFix();

      for (MetricFix dm: metricFixList) {    
        if (projectQuota.getMetric().equals(dm.getMetric())) {
          flag = true;
          metricFix = dm;
          break;
        } 
      }

      if (flag) {
        String metricValueStr = entry.getValue().toString();  
        long metricValue = Long.parseLong(metricValueStr);
        metricValue = metricValue/metricFix.getFixer();
        projectQuota.setMetricValue(Long.toString(metricValue));
      } else {
        projectQuota.setMetricValue(entry.getValue().toString());
      }

      projectQuota.setMetricValueType(METRIC_VALUE_USAGE);
    }
    projectQuota.setVpcName("NA");
    return projectQuota;
  }

  /*
   * API to build BigQuery row content from ProjectQuota object
   * */
  public static Map<String, Object> createBQRow(ProjectQuota projectQuota) {
    Map<String, Object> rowContent = new HashMap<>();
    rowContent.put("threshold", projectQuota.getThreshold());
    rowContent.put("region", projectQuota.getRegion());
    rowContent.put("m_value", projectQuota.getMetricValue());
    rowContent.put("mv_type", projectQuota.getMetricValueType());
    rowContent.put("vpc_name", projectQuota.getVpcName());
    rowContent.put("metric", projectQuota.getMetric());
    rowContent.put("addedAt", projectQuota.getTimestamp());
    rowContent.put("project_id", projectQuota.getProjectId());
    rowContent.put("folder_id", projectQuota.getFolderId());
    rowContent.put("targetpool_name", projectQuota.getTargetPoolName());
    rowContent.put("org_id", projectQuota.getOrgId());
    return rowContent;
  }

  /*
   * API to insert row in table
   * */
  public static void tableInsertRows(
      GCPResourceClient gcpResourceClient, Map<String, Object> rowContent) {

    try {
      // Initialize client that will be used to send requests. This client only needs to be created
      // once, and can be reused for multiple requests.
      BigQuery bigquery = gcpResourceClient.getBigQuery();
      // Get table
      TableId tableId = gcpResourceClient.getTableId();
      // Inserts rowContent into datasetName:tableId.
      InsertAllResponse response =
          bigquery.insertAll(InsertAllRequest.newBuilder(tableId).addRow(rowContent).build());

      if (response.hasErrors()) {
        // If any of the insertions failed, this lets you inspect the errors
        for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
          logger.log(Level.SEVERE, "Bigquery row insert response error: " + entry.getValue());
        }
      }
    } catch (BigQueryException e) {
      logger.log(Level.SEVERE, "Insert operation not performed: " + e.toString());
    }
  }
}
