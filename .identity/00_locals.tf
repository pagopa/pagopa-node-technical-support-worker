locals {
  github = {
    org        = "pagopa"
    repository = "<your-project-name>" # TODO set the project repository
  }
  prefix = "pagopa"
  product = "${local.prefix}-${var.env_short}"
  project = "${local.prefix}-${var.env_short}-${local.location_short}-${local.domain}"
  runner = "${local.prefix}-${var.env_short}-${local.location_short}"

  domain = "project-domain" # TODO set the project domain (i.e. namespace)
  location_short  = "weu"

  aks_name                = "${local.product}-${local.location_short}-${var.env}-aks"
  aks_resource_group_name = "${local.product}-${local.location_short}-${var.env}-aks-rg"

  pagopa_apim_name = "${local.product}-apim"
  pagopa_apim_rg   = "${local.product}-api-rg"
}
