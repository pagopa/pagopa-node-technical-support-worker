prefix    = "pagopa"
env       = "prod"
env_short = "p"
location_short  = "weu"

tags = {
  CreatedBy   = "Terraform"
  Environment = "Prod"
  Owner       = "pagoPA"
  Source      = "https://github.com/pagopa/pagopa-node-technical-support-worker"
  CostCenter  = "TS310 - PAGAMENTI & SERVIZI"
}

apim_dns_zone_prefix               = "platform"
external_domain                    = "pagopa.it"
hostname = "weuprod.nodo.internal.platform.pagopa.it"

# Application (client) id of the Nodo Observability frontend app registration on Entra ID
nodo_observability_entra_client_id = "0e79c251-fd4f-4600-aa77-312e4ce68c72"
