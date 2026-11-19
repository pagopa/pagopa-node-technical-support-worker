prefix    = "pagopa"
env       = "uat"
env_short = "u"
location_short  = "weu"

tags = {
  CreatedBy   = "Terraform"
  Environment = "Uat"
  Owner       = "pagoPA"
  Source      = "https://github.com/pagopa/pagopa-node-technical-support-worker"
  CostCenter  = "TS310 - PAGAMENTI & SERVIZI"
}

apim_dns_zone_prefix               = "uat.platform"
external_domain                    = "pagopa.it"
hostname = "weuuat.nodo.internal.uat.platform.pagopa.it"

# Application (client) id of the Nodo Observability frontend app registration on Entra ID
nodo_observability_entra_client_id = "<entra-client-id>"
