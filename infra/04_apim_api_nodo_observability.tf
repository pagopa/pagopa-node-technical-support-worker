locals {
  nodo_observability_api = {
    display_name          = "Nodo Observability"
    description           = "API di Osservabilità del Nodo dei Pagamenti"
    path                  = "nodo/observability"
    subscription_required = true
    service_url           = null
    product_id            = "product-nodo-observability"
  }
}

resource "azurerm_api_management_api_version_set" "observability_api_version_set" {
  name                = "${local.project}-observability-api"
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.nodo_observability_api.display_name
  versioning_scheme   = "Segment"
}

module "observability_api_v1" {
  source = "git::https://github.com/pagopa/terraform-azurerm-v3.git//api_management_api?ref=v6.7.0"

  name                  = azurerm_api_management_api_version_set.observability_api_version_set.name
  resource_group_name   = local.apim.rg
  api_management_name   = local.apim.name
  product_ids           = [local.nodo_observability_api.product_id]
  subscription_required = false

  version_set_id = azurerm_api_management_api_version_set.observability_api_version_set.id
  api_version    = "v1"

  description  = local.nodo_observability_api.description
  display_name = local.nodo_observability_api.display_name
  path         = local.nodo_observability_api.path
  protocols    = ["https"]

  service_url = local.nodo_observability_api.service_url

  content_format = "openapi"
  # only the operations called by the nodo-observability frontend, see scripts/observability_openapi.sh
  content_value = templatefile("../openapi/observability_openapi.json", {
    host    = local.host,
    service = local.nodo_observability_api.product_id
  })

  xml_content = templatefile("./policy/nodo_observability/api_group_base_policy.xml", {
    hostname      = var.hostname
    frontendUrl   = "https://pagopa${var.env_short}weunodoobsvsa.z6.web.core.windows.net"
    localUrl      = var.env_short == "d" ? "<origin>http://localhost:4201</origin>": ""
    entraTenantId = data.azurerm_client_config.current.tenant_id
    entraClientId = var.nodo_observability_entra_client_id
  })
}

resource "azurerm_api_management_api_operation_policy" "api_events_policy" {

  api_name              = module.observability_api_v1.name
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg

  operation_id          = "get-api-events"

  xml_content = file("./policy/nodo_observability/api_events_base_policy.xml")
}
