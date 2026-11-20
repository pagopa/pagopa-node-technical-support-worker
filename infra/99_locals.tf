locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
    product_id = "technical_support_api"
  }

  display_name          = "Nodo Technical Support"
  description           = "API Assistenza del Nodo dei Pagamenti"
  path                  = "technical-support/ndp/api"

  host         = "api.${var.apim_dns_zone_prefix}.${var.external_domain}"
  hostname     = var.hostname

  subscription_required = true
  service_url           = null
}

