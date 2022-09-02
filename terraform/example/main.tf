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

terraform {
  required_providers {
    google = {
      version = ">3.5.0"
    }
  }
}

provider "google" {
  credentials = file("${var.creds_file}")
  project     = var.project_id
  region      = var.region
}

module "qms" {
  source = "../modules/qms"

  project_id                    = var.project_id
  region                        = var.region
  service_account_email         = var.service_account_email
  folders                       = var.folders
  organizations                 = var.organizations
  alert_log_bucket_name         = var.alert_log_bucket_name
  notification_email_address    = var.notification_email_address
  threshold                     = var.threshold
}