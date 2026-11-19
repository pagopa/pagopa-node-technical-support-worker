# general

variable "prefix" {
  type = string
  validation {
    condition = (
      length(var.prefix) <= 6
    )
    error_message = "Max length is 6 chars."
  }
}

variable "env" {
  type = string
}

variable "env_short" {
  type = string
  validation {
    condition = (
      length(var.env_short) == 1
    )
    error_message = "Length must be 1 chars."
  }
}

variable "location_short" {
  type = string
  validation {
    condition = (
      length(var.location_short) == 3
    )
    error_message = "Length must be 3 chars."
  }
  description = "One of wue, neu"
}

variable "tags" {
  type = map(any)
  default = {
    CreatedBy = "Terraform"
  }
}

variable "apim_dns_zone_prefix" {
  type        = string
  default     = null
  description = "The dns subdomain for apim."
}

variable "external_domain" {
  type        = string
  default     = null
  description = "Domain for delegation"
}

variable "hostname" {
  type        = string
  default     = null
  description = "Hostname for the API"
}

variable "domain" {
  type = string
  validation {
    condition = (
      length(var.domain) <= 12
    )
    error_message = "Max length is 12 chars."
  }
  default = "nodo"
}

variable "nodo_observability_entra_tenant_id" {
  type        = string
  default     = "7788edaf-0346-4068-9d79-c868aed15b3d"
  description = "Entra ID tenant that issues the tokens of the Nodo Observability frontend"
}

variable "nodo_observability_entra_client_id" {
  type        = string
  description = "Application (client) id of the Nodo Observability frontend app registration on Entra ID"
}
