locals {
  repo_name = "pagopa-node-technical-support-worker"

  display_name          = "Nodo Technical Support"
  description           = "API Assistenza del Nodo dei Pagamenti"
  path                  = "technical-support/ndp/api"

  host         = "api.${var.apim_dns_zone_prefix}.${var.external_domain}"
  hostname     = var.hostname

  subscription_required = true
  service_url           = null

}

resource "azurerm_api_management_group" "api_group" {
  name                = local.apim.product_id
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.display_name
  description         = local.description
}

resource "azurerm_api_management_api_version_set" "api_version_set" {
  name                = format("%s-technical-support-api", local.project)
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.display_name
  versioning_scheme   = "Segment"
}

module "api_v1" {
  source = "git::https://github.com/pagopa/terraform-azurerm-v3.git//api_management_api?ref=v6.7.0"

  name                  = format("%s-technical-support-api", local.project)
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  product_ids           = [local.apim.product_id]
  subscription_required = local.subscription_required

  version_set_id = azurerm_api_management_api_version_set.api_version_set.id
  api_version    = "v1"

  description  = local.description
  display_name = local.display_name
  path         = local.path
  protocols    = ["https"]

  service_url = local.service_url

  content_format = "openapi"
  content_value  = templatefile("../openapi/openapi.json", {
    host = local.host,
    service = local.apim.product_id
  })

  xml_content = templatefile("./policy/_base_policy.xml", {
    hostname = var.hostname
  })
}

