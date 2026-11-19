prefix    = "pagopa"
env       = "dev"
env_short = "d"
location_short  = "weu"

tags = {
  CreatedBy   = "Terraform"
  Environment = "Dev"
  Owner       = "pagoPA"
  Source      = "https://github.com/pagopa/pagopa-node-technical-support-worker"
  CostCenter  = "TS310 - PAGAMENTI & SERVIZI"
}

apim_dns_zone_prefix               = "dev.platform"
external_domain                    = "pagopa.it"
hostname = "weudev.nodo.internal.dev.platform.pagopa.it"

# Application (client) id of the Nodo Observability frontend app registration on Entra ID
nodo_observability_entra_client_id = "37cf8cca-a086-4828-a115-26367dd4c00f"