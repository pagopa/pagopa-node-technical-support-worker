data "http" "template_decoupler_policy" {
  url = "https://raw.githubusercontent.com/pagopa/pagopa-infra/main/src/domains/nodo-app/api_product/legacy/nodo_pagamenti_api/decoupler/base_policy.xml.tpl"
}

resource "azurerm_api_management_api_version_set" "api_version_set_ndp_direct" {
  name                = format("%s-technical-support-api-ndp-direct", local.project)
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = "${local.display_name} direct"
  versioning_scheme   = "Segment"
}

module "api_ndp_direct" { # direct API to NDP
  source = "git::https://github.com/pagopa/terraform-azurerm-v3.git//api_management_api?ref=v6.7.0"

  name                  = format("%s-technical-support-api-ndp-direct", local.project)
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  product_ids           = [local.apim.product_id]
  subscription_required = local.subscription_required

  version_set_id = azurerm_api_management_api_version_set.api_version_set_ndp_direct.id
  api_version    = "v1"

  description  = "${local.description} direct"
  display_name = "${local.display_name} direct"
  path         = "${local.path}/nodod" // technical-support/ndp/api where "d" stay for direct
  protocols    = ["https"]

  service_url = local.service_url


  content_format = "openapi"
  # content_value  = templatefile("../openapi/_swagger_chk_pos_only.json.tpl", {
  #   host = local.host,
  #   service = local.apim.product_id
  # })
  # content_value  = file("${path.module}/../openapi/nodoTechnicalSupport_direct_openapi.json")
  content_value  = data.local_file.openapi_direct.content

  xml_content = templatestring(data.http.template_decoupler_policy.body, { # we consider decoupler ON 👀👇
    # address-range-from       = var.env_short == "p" ? "10.1.128.0" : "0.0.0.0"
    # address-range-to         = var.env_short == "p" ? "10.1.128.255" : "0.0.0.0"
    address-range-from       = "0.0.0.0"
    address-range-to         = "0.0.0.0"
    is-nodo-auth-pwd-replace = false
  })

}

# resource "terraform_data" "merge_openapi_script" {
#   # Forza la riesecuzione ogni volta che cambia l'hash dello script o un input
#   triggers_replace = [
#   #   filesha256("${path.module}/scripts/merge_openapi.sh")
#     timestamp()
#   ]
#
#
#   provisioner "local-exec" {
#     command     = "/bin/bash ${path.module}/../scripts/merge_openapi.sh"
#   }
# }


data "external" "merge_openapi_script" {
  program = ["/bin/bash", "${path.module}/../scripts/wrapper_merge_openapi.sh"]
}

data "local_file" "openapi_direct" {
  filename = "${path.module}/../openapi/nodoTechnicalSupport_direct_openapi.json"

  # Forza la lettura del file DOPO l'esecuzione dello script nel plan
  depends_on = [data.external.merge_openapi_script]
}
