locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
    product_id = "apiconfig-selfcare-integration"
    hostname = "api.${var.apim_dns_zone_prefix}.${var.external_domain}"
  }
  nodo = {
    hostname = var.env == "prod" ? "${var.location_short}${var.env}.${var.domain}.internal.platform.pagopa.it" : "${var.location_short}${var.env}.${var.domain}.internal.${var.env}.platform.pagopa.it"
  }
}

