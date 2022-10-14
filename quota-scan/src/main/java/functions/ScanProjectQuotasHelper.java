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

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.monitoring.v3.QueryServiceClient.QueryTimeSeriesPagedResponse;
import com.google.cloud.monitoring.v3.QueryServiceClient;
import com.google.monitoring.v3.QueryTimeSeriesRequest;
import com.google.monitoring.v3.TimeSeriesData;
import functions.eventpojos.GCPProject;
import functions.eventpojos.GCPResourceClient;
import functions.eventpojos.ProjectQuota;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScanProjectQuotasHelper {
  private static final Logger logger = Logger.getLogger(ScanProjectQuotasHelper.class.getName());

  public static final String MQL_ALLOCATION_ALL = "fetch consumer_quota" +
    "| filter resource.service =~ '.*'" +
    "| { usage:" +
    "     metric 'serviceruntime.googleapis.com/quota/allocation/usage'" +
    "     | filter resource.project_id = '%1$s'" +
    "     | align next_older(1d)" +
    "     | group_by" +
    "         [resource.service, resource.project_id, resource.location," +
    "         metric.quota_metric]," +
    "         [value_usage_aggregate: aggregate(value.usage)," +
    "         value_usage_max: max(value.usage), value_usage_min: min(value.usage)]" +
    " ; limit:" +
    "     metric 'serviceruntime.googleapis.com/quota/limit'" +
    "     | filter resource.project_id = '%1$s'" +
    "     | align next_older(1d)" +
    "     | group_by" +
    "         [resource.service, resource.project_id, resource.location," +
    "         metric.quota_metric, metric.limit_name]," +
    "         [value_limit_aggregate: aggregate(value.limit)] }" +
    "| join" +
    "| value" +
    "   [limit: limit.value_limit_aggregate, usage: usage.value_usage_aggregate," +
    "   usage.value_usage_max, usage.value_usage_min]";
    public static final String MQL_RATE_ALL = "fetch consumer_quota" +
    "| filter resource.service =~ '.*'" +
    "| { usage:" +
    "     metric 'serviceruntime.googleapis.com/quota/rate/net_usage'" +
    "     | filter resource.project_id = '%1$s'" +
    "     | align next_older(1d)" +
    "     | group_by" +
    "         [resource.service, resource.project_id, resource.location," +
    "         metric.quota_metric]," +
    "         [value_usage_aggregate: aggregate(value.usage)," +
    "         value_usage_max: max(value.usage), value_usage_min: min(value.usage)]" +
    " ; limit:" +
    "     metric 'serviceruntime.googleapis.com/quota/limit'" +
    "     | filter resource.project_id = '%1$s'" +
    "     | align next_older(1d)" +
    "     | group_by" +
    "         [resource.service, resource.project_id, resource.location," +
    "         metric.quota_metric, metric.limit_name]," +
    "         [value_limit_aggregate: aggregate(value.limit)] }" +
    "| join" +
    "| value" +
    "   [limit: limit.value_limit_aggregate, usage: usage.value_usage_aggregate," +
    "   usage.value_usage_max, usage.value_usage_min]";
  
  public static final String METRIC_VALUE_USAGE = "usage";
  public static final String METRIC_VALUE_LIMIT = "limit";

  enum Quotas {
    ALLOCATION,
    RATE
  }

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

  public static List<ProjectQuota> getQuota(GCPProject gcpProject, Quotas quota) {
    List<ProjectQuota> projectQuotas = new ArrayList<>();

    try (QueryServiceClient queryServiceClient = QueryServiceClient.create()) {
      QueryTimeSeriesRequest request =
          QueryTimeSeriesRequest.newBuilder()
              .setName(gcpProject.getProjectName())
              .setQuery(String.format(getMql(quota), gcpProject.getProjectId()))
              .build();

      Timestamp ts = Timestamp.now();
      QueryTimeSeriesPagedResponse response = queryServiceClient.queryTimeSeries(request);
      for (TimeSeriesData data : response.iterateAll()) {
        projectQuotas.add(populateProjectQuota(data, ts, METRIC_VALUE_LIMIT));
        projectQuotas.add(populateProjectQuota(data, ts, METRIC_VALUE_USAGE));
      }
    } catch (IOException e) {
      logger.log(
          Level.SEVERE,
          "Error fetching timeseries data for project: "
              + gcpProject.getProjectName()
              + e.getMessage(),
          e);
    }

    return projectQuotas;
  }

  private static ProjectQuota populateProjectQuota(
      TimeSeriesData data, Timestamp ts, String metricType) {
    ProjectQuota projectQuota = new ProjectQuota();

    projectQuota.setThreshold(Integer.valueOf(THRESHOLD));
    projectQuota.setProjectId(data.getLabelValues(1).getStringValue());
    projectQuota.setTimestamp(ts.toString());
    projectQuota.setRegion(data.getLabelValues(2).getStringValue());
    projectQuota.setMetric(data.getLabelValues(3).getStringValue());
    projectQuota.setMetricValueType(metricType);
    if(metricType == METRIC_VALUE_LIMIT) {
      projectQuota.setMetricValue(String.valueOf(data.getPointData(0).getValues(0).getInt64Value()));
    }
    else {
      projectQuota.setMetricValue(String.valueOf(data.getPointData(0).getValues(1).getInt64Value()));
    }

    projectQuota.setOrgId("orgId");
    projectQuota.setFolderId("NA");
    projectQuota.setTargetPoolName("NA");
    projectQuota.setVpcName("NA");
    return projectQuota;
  }

  private static String getMql(Quotas q) {
    String mql;

    switch (q) {
      case ALLOCATION:
        mql = MQL_ALLOCATION_ALL;
        break;
      case RATE:
        mql = MQL_RATE_ALL;
        break;
      default:
        mql = "";
    }

    return mql;
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
