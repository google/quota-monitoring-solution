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

package functions.eventpojos;

/*
 * POJO for ProjectQuota
 * */
public class ProjectQuota {
  private String projectId;
  private String timestamp;
  private String region;
  private String metric;
  private String limitName;
  private Long currentUsage;
  private Long maxUsage;
  private Long quotaLimit;
  private Integer threshold;

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = metric;
  }

  public String getLimitName() {
    return limitName;
  }

  public void setLimitName(String limitName) {
    this.limitName = limitName;
  }
  
  public Long getCurrentUsage() {
    return currentUsage;
  }

  public void setCurrentUsage(Long currentUsage) {
    this.currentUsage = currentUsage;
  }
  
  public Long getMaxUsage() {
    return maxUsage;
  }

  public void setMaxUsage(Long maxUsage) {
    this.maxUsage = maxUsage;
  }
  
  public Long getQuotaLimit() {
    return quotaLimit;
  }

  public void setQuotaLimit(Long quotaLimit) {
    this.quotaLimit = quotaLimit;
  }
  
  public Integer getThreshold() {
    return threshold;
  }

  public void setThreshold(Integer threshold) {
    this.threshold = threshold;
  }
}
